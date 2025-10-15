/*******************************************************************************
 * Copyright (c) 2009, 2025 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import org.jacoco.core.internal.analysis.filter.Filters;
import org.jacoco.core.internal.analysis.filter.IFilter;
import org.jacoco.core.internal.analysis.filter.IFilterContext;
import org.jacoco.core.internal.analysis.filter.KotlinSMAP;
import org.jacoco.core.internal.diff.ClassInfoDto;
import org.jacoco.core.internal.diff.DiffCodeDto;
import org.jacoco.core.internal.flow.ClassProbesVisitor;
import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.jacoco.core.internal.instr.InstrSupport;
import org.jacoco.core.tools.ExecFileLoader;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Analyzes the structure of a class.
 */
public class ClassAnalyzer extends ClassProbesVisitor
        implements IFilterContext {

    private final ClassCoverageImpl coverage;
    private boolean[] probes;
    private final StringPool stringPool;

    private final Set<String> classAnnotations = new HashSet<String>();

    private final Set<String> classAttributes = new HashSet<String>();

    private String sourceDebugExtension;
    private KotlinSMAP smap;
    private final HashMap<String, SourceNodeImpl> fragments = new HashMap<String, SourceNodeImpl>();

    private final IFilter filter;

    // create by xulingjian 2024-10-21
    private DiffCodeDto diffCodes;

    // create by xulingjian 2024-10-21
    // 只收集方法中的指令的覆盖率，在收集到指令后退出后面的分析流程
    private boolean onlyAnaly = false;

    /**
     * Creates a new analyzer that builds coverage data for a class.
     *
     * @param coverage   coverage node for the analyzed class data
     * @param probes     execution data for this class or <code>null</code>
     * @param stringPool shared pool to minimize the number of {@link String} instances
     */
    public ClassAnalyzer(final ClassCoverageImpl coverage,
                         final boolean[] probes, final StringPool stringPool) {
        this.coverage = coverage;
        this.probes = probes;
        this.stringPool = stringPool;
        this.filter = Filters.all();
    }

    // create by xulingjian 2024-10-21
    public ClassAnalyzer(final ClassCoverageImpl coverage,
                         final boolean[] probes, final StringPool stringPool,
                         DiffCodeDto diffCodes, boolean onlyAnaly) {
        this.coverage = coverage;
        this.probes = probes;
        this.stringPool = stringPool;
        this.filter = Filters.all();
        this.diffCodes = diffCodes;
        this.onlyAnaly = onlyAnaly;
    }

    // create by xulingjian 2024-10-21
    public DiffCodeDto getDiffCodes() {
        return diffCodes;
    }

    // create by xulingjian 2024-10-21
    public void setDiffCodes(DiffCodeDto diffCodes) {
        this.diffCodes = diffCodes;
    }

    @Override
    public void visit(final int version, final int access, final String name,
                      final String signature, final String superName,
                      final String[] interfaces) {
        coverage.setSignature(stringPool.get(signature));
        coverage.setSuperName(stringPool.get(superName));
        coverage.setInterfaces(stringPool.get(interfaces));
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc,
                                             final boolean visible) {
        classAnnotations.add(desc);
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public void visitAttribute(final Attribute attribute) {
        classAttributes.add(attribute.type);
    }

    @Override
    public void visitSource(final String source, final String debug) {
        coverage.setSourceFileName(stringPool.get(source));
        sourceDebugExtension = debug;
    }

    @Override
    public MethodProbesVisitor visitMethod(final int access, final String name,
                                           final String desc, final String signature,
                                           final String[] exceptions) {

        InstrSupport.assertNotInstrumented(name, coverage.getName());

        final InstructionsBuilder builder = new InstructionsBuilder(probes);

//        return new MethodAnalyzer(builder) {
//
//            @Override
//            public void accept(final MethodNode methodNode,
//                               final MethodVisitor methodVisitor) {
//                super.accept(methodNode, methodVisitor);
//                addMethodCoverage(stringPool.get(name), stringPool.get(desc),
//                        stringPool.get(signature), builder, methodNode);
//            }
//        };

        // create by xulingjian 2024-10-21
        // 对方法解析完毕后的一个钩子方法，从visitMethod的mv对象调用过来
        return new MethodAnalyzer(builder) {

            @Override
            public void accept(final MethodNode methodNode,
                               final MethodVisitor methodVisitor) {
                // 统计method的方法体的指令级别覆盖率，指令级别需要关注的是braches和coverbraches，line代码合并不需要关注，染色用
                String coverageName = coverage.getName();
                System.out.printf("access: %s name: %s desc: %s signature: %s coverageName: %s\n", access, name, desc, signature, coverageName);
                String methodSign = access + name + desc + signature;
                super.accept(methodNode, methodVisitor);
                System.out.printf("methodSign: %s coverageName: %s exceptions: %b\n", methodSign, coverageName, exceptions == null);
                // 合并多版本覆盖率的时候不要走后面addMethodCoverage的流程，只获取到指令覆盖率就行
                if (exceptions != null) {
                    for (String s : exceptions) {
                        methodSign += s;
                    }
                }
                Map<String, Map<String, Map<String, Instruction>>> instrunctions = ExecFileLoader.instrunctionsThreadLocal.get();
                Map<String, boolean[]> probesMap = ExecFileLoader.probesMap.get();
//                if (probesMap != null) {
//                    System.out.printf("methodSign: %s coverageName: %s probesMap: %s onlyAnaly: %b\n", methodSign, coverage.getName(), new Gson().toJson(probesMap), onlyAnaly);
//                } else {
//                    System.out.printf("methodSign: %s coverageName: %s onlyAnaly: %b\n", methodSign, coverage.getName(), onlyAnaly);
//                }
                if (onlyAnaly) {
                    Map<String, Map<String, Instruction>> methodInstructions = new HashMap<>();
                    Map<String, Instruction> instructionMap = new HashMap<>();
                    Map<AbstractInsnNode, Instruction> builderInstructions = builder.getInstructions();
                    for (Instruction instruction : builderInstructions.values()) {
                        instructionMap.put(instruction.getSign(), instruction);
                    }
                    methodInstructions.put(methodSign, instructionMap);
                    if (instrunctions == null) {
                        instrunctions = new HashMap<>();
                        instrunctions.put(coverageName, methodInstructions);
                        ExecFileLoader.instrunctionsThreadLocal.set(instrunctions);
                    } else {
                        if (instrunctions.containsKey(coverageName)) {
                            instrunctions.get(coverageName).put(methodSign, instructionMap);
                        } else {
                            instrunctions.put(coverageName, methodInstructions);
                        }
                    }
                    System.out.printf("coverage name: %s probesMap: %b\n", coverageName, probesMap == null);
                    if (probesMap == null) {
                        probesMap = new HashMap<>();
                        probesMap.put(coverageName, probes);
                        ExecFileLoader.probesMap.set(probesMap);
                    } else {
                        probesMap.put(coverageName, probes);
                    }
//                    System.out.printf("coverage name: %s probesMap: %s\n", coverage.getName(), new Gson().toJson(probesMap));
                    return;
                }
                // 如果存在已有的覆盖率数据，则合并method的指令覆盖率
                if (instrunctions != null && instrunctions.containsKey(coverageName)) {
                    System.out.printf("coverage name: %s merge\n", coverageName);
                    // 合并method的指令数据
                    Map<String, Instruction> mergeInstructionMap = instrunctions.get(coverageName).get(methodSign);
                    // 通过指令判断是否为同一个方法，所有指令签名一样的情况下判断是一样的
                    Map<AbstractInsnNode, Instruction> nowInstructions = builder.getInstructionsNotWireJumps();
                    if (mergeInstructionMap != null && mergeInstructionMap.size() == nowInstructions.size()) {
                        boolean isSameMethod = true;
                        for (final Instruction instruction : mergeInstructionMap.values()) {
                            Optional<Instruction> optionalInstruction = nowInstructions.values().stream().filter(i -> i.getSign().equals(instruction.getSign())).findAny();
                            if (!optionalInstruction.isPresent()) {
                                isSameMethod = false;
                                break;
                            }
                        }
                        System.out.printf("coverage name: %s isSameMethod: %b\n", coverageName, isSameMethod);
                        // 同一个方法
                        if (isSameMethod) {
                            //合并exec新方案--直接合并两个probes对应的探针
                            Map<String, boolean[]> mergeProbesMap = ExecFileLoader.probesMap.get();
                            Optional<Instruction> instructionOptional = nowInstructions.values().stream().filter(i -> i.getProbeIndex() >= 0).min(Comparator.comparingInt(Instruction::getProbeIndex));
                            System.out.printf("coverage name: %s probes: %b instructionOptional: %b\n", coverageName, probes != null, instructionOptional.isPresent());
                            if (instructionOptional.isPresent()) {
                                int probeStart = instructionOptional.get().getProbeIndex();
                                int probeEnd = nowInstructions.values().stream().filter(i -> i.getProbeIndex() >= 0).max(Comparator.comparingInt(Instruction::getProbeIndex)).get().getProbeIndex();
                                int mergeProbeStart = mergeInstructionMap.values().stream().filter(i -> i.getProbeIndex() >= 0).min(Comparator.comparingInt(Instruction::getProbeIndex)).get().getProbeIndex();
                                int mergeProbeEnd = mergeInstructionMap.values().stream().filter(i -> i.getProbeIndex() >= 0).max(Comparator.comparingInt(Instruction::getProbeIndex)).get().getProbeIndex();
                                System.out.printf("coverage name: %s probeStart: %d probeEnd: %d mergeProbeStart: %d mergeProbeEnd: %d\n", coverageName, probeStart, probeEnd, mergeProbeStart, mergeProbeEnd);
                                if(probes == null){
                                    probes = new boolean[probeEnd - probeStart + 1];
                                }
                                //jacoco是以方法级别进行插桩的，所以理论上同个方法的探针的长度是一样的
                                assert probeEnd - probeStart == mergeProbeEnd - mergeProbeStart;
                                System.out.printf("coverage name: %s mergeProbesMap: %b\n", coverageName, mergeProbesMap != null);
                                if (mergeProbesMap != null) {
                                    System.out.printf("coverage name: %s mergeProbesMap containsKey: %b\n", coverageName, mergeProbesMap.containsKey(coverageName));
                                }
                                if (mergeProbesMap != null && mergeProbesMap.containsKey(coverageName)) {
                                    System.out.printf("coverage name: %s merge probe\n", coverageName);
                                    boolean[] mergeProbes = mergeProbesMap.get(coverageName);
                                    if (mergeProbes != null) {
                                        int currentIndex = mergeProbeStart;
                                        System.out.printf("coverage name: %s currentIndex: %s mergeProbes: %s\n", coverageName, currentIndex, mergeProbes.length);
                                        for (int k = probeStart; k < probeEnd + 1; k++) {
                                            if (mergeProbes[currentIndex]) {
                                                probes[k] = true;
                                            }
                                            currentIndex++;
                                        }
                                    } else {
                                        System.out.printf("coverage name: %s mergeProbes is null\n", coverageName);
                                    }
                                }
                            }
                            //合并指令
                            for (AbstractInsnNode key : nowInstructions.keySet()) {
                                System.out.printf("coverage name: %s key\n", coverageName);
                                Instruction instruction = nowInstructions.get(key);
                                //合并指令
                                Instruction other = mergeInstructionMap.get(instruction.getSign());
                                Instruction merge = instruction.mergeNew(other);
                                nowInstructions.put(key, merge);
                            }
                        }
                    }
                }
                addMethodCoverage(stringPool.get(name), stringPool.get(desc), stringPool.get(signature), builder, methodNode);
            }
        };
    }

    private void addMethodCoverage(final String name, final String desc,
                                   final String signature, final InstructionsBuilder icc,
                                   final MethodNode methodNode) {

        final Map<AbstractInsnNode, Instruction> instructions = icc
                .getInstructions();
        calculateFragments(instructions);

        final MethodCoverageCalculator mcc = new MethodCoverageCalculator(
                instructions);
        filter.filter(methodNode, this, mcc);

        final MethodCoverageImpl mc = new MethodCoverageImpl(name, desc,
                signature);
        mcc.calculate(mc);
        // method的级别级别的计算，指令级别是最小的行覆盖率，这是整个覆盖率计算的基础
        if (mc.containsCode()) {
            // Only consider methods that actually contain code
            coverage.addMethod(mc);
        }

    }

    private void calculateFragments(
            final Map<AbstractInsnNode, Instruction> instructions) {
        if (sourceDebugExtension == null || !Filters.isKotlinClass(this)) {
            return;
        }
        if (smap == null) {
            // Note that visitSource is invoked before visitAnnotation,
            // that's why parsing is done here
            smap = new KotlinSMAP(getSourceFileName(), sourceDebugExtension);
        }
        for (final KotlinSMAP.Mapping mapping : smap.mappings()) {
            if (coverage.getName().equals(mapping.inputClassName())
                    && mapping.inputStartLine() == mapping.outputStartLine()) {
                continue;
            }
            SourceNodeImpl fragment = fragments.get(mapping.inputClassName());
            if (fragment == null) {
                fragment = new SourceNodeImpl(null, mapping.inputClassName());
                fragments.put(mapping.inputClassName(), fragment);
            }
            final int mappingOutputEndLine = mapping.outputStartLine()
                    + mapping.repeatCount() - 1;
            for (Instruction instruction : instructions.values()) {
                if (mapping.outputStartLine() <= instruction.getLine()
                        && instruction.getLine() <= mappingOutputEndLine) {
                    final int originalLine = mapping.inputStartLine()
                            + instruction.getLine() - mapping.outputStartLine();
                    fragment.increment(instruction.getInstructionCounter(),
                            CounterImpl.COUNTER_0_0, originalLine);
                }
            }
        }
    }

    @Override
    public FieldVisitor visitField(final int access, final String name,
                                   final String desc, final String signature, final Object value) {
        InstrSupport.assertNotInstrumented(name, coverage.getName());
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public void visitTotalProbeCount(final int count) {
        // nothing to do
    }

    @Override
    public void visitEnd() {
        if (!fragments.isEmpty()) {
            coverage.setFragments(Arrays
                    .asList(fragments.values().toArray(new SourceNodeImpl[0])));
        }
    }

    // IFilterContext implementation

    public String getClassName() {
        return coverage.getName();
    }

    public String getSuperClassName() {
        return coverage.getSuperName();
    }

    public Set<String> getClassAnnotations() {
        return classAnnotations;
    }

    public Set<String> getClassAttributes() {
        return classAttributes;
    }

    public String getSourceFileName() {
        return coverage.getSourceFileName();
    }

    public String getSourceDebugExtension() {
        return sourceDebugExtension;
    }

}
