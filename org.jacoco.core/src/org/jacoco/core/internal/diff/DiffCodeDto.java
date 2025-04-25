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
package org.jacoco.core.internal.diff;

import java.util.List;

/**
 * @author xulingjian
 */
public class DiffCodeDto {
    /**
     * 排除类
     */
    private List<ClassInfoDto> excludes;

    /**
     * 统计类
     */
    private List<ClassInfoDto> includes;


    public List<ClassInfoDto> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<ClassInfoDto> excludes) {
        this.excludes = excludes;
    }

    public List<ClassInfoDto> getIncludes() {
        return includes;
    }

    public void setIncludes(List<ClassInfoDto> includes) {
        this.includes = includes;
    }
}
