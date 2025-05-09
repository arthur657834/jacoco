/*******************************************************************************
 * Copyright (c) 2009, 2025 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Nikolay Krasko - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filter;

import org.jacoco.core.internal.instr.InstrSupport;
import org.junit.Test;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

/**
 * Unit tests for {@link KotlinGeneratedFilter}.
 */
public class KotlinGeneratedFilterTest extends FilterTestBase {

	private final IFilter filter = new KotlinGeneratedFilter();

	@Test
	public void testNoLinesForKotlinWithDebug() {
		final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
				"hashCode", "()I", null, null);
		m.visitInsn(Opcodes.ICONST_0);
		m.visitInsn(Opcodes.IRETURN);
		context.classAnnotations
				.add(KotlinGeneratedFilter.KOTLIN_METADATA_DESC);

		filter.filter(m, context, output);

		assertMethodIgnored(m);
	}

	@Test
	public void testWithLinesForKotlinWithDebug() {
		final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
				"hashCode", "()I", null, null);
		m.visitAnnotation("Lother/Annotation;", false);
		m.visitLineNumber(12, new Label());
		m.visitInsn(Opcodes.ICONST_0);
		m.visitInsn(Opcodes.IRETURN);
		context.classAnnotations
				.add(KotlinGeneratedFilter.KOTLIN_METADATA_DESC);

		filter.filter(m, context, output);

		assertIgnored(m);
	}

	@Test
	public void testNoLinesForKotlinNoDebug() {
		final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
				"hashCode", "()I", null, null);
		m.visitInsn(Opcodes.ICONST_0);
		m.visitInsn(Opcodes.IRETURN);
		context.classAnnotations
				.add(KotlinGeneratedFilter.KOTLIN_METADATA_DESC);
		context.sourceFileName = null;

		filter.filter(m, context, output);

		assertIgnored(m);
	}

	@Test
	public void testWithLinesForKotlinNoDebug() {
		final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
				"hashCode", "()I", null, null);
		m.visitInsn(Opcodes.ICONST_0);
		m.visitInsn(Opcodes.IRETURN);
		m.visitLineNumber(12, new Label());
		context.classAnnotations
				.add(KotlinGeneratedFilter.KOTLIN_METADATA_DESC);
		context.sourceFileName = null;

		filter.filter(m, context, output);

		assertIgnored(m);
	}

	/**
	 * <pre>
	 * inline fun f() {
	 * }
	 * </pre>
	 */
	@Test
	public void should_filter_instructions_without_line_numbers() {
		context.sourceFileName = "Example.kt";
		context.classAnnotations
				.add(KotlinGeneratedFilter.KOTLIN_METADATA_DESC);
		final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
				"f", "()V", null, null);
		m.visitInsn(Opcodes.ICONST_0);
		final Range range1 = new Range(m.instructions.getLast(),
				m.instructions.getLast());
		m.visitVarInsn(Opcodes.ISTORE, 1);
		final Range range2 = new Range(m.instructions.getLast(),
				m.instructions.getLast());
		m.visitLineNumber(1, new Label());
		m.visitInsn(Opcodes.RETURN);

		filter.filter(m, context, output);

		assertIgnored(m, range1, range2);
	}

}
