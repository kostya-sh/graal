/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.lir.phases;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.lir.gen.MoveFactory;

public abstract class AllocationPhase extends LIRPhase<AllocationPhase.AllocationContext> {

    public static final class AllocationContext extends GenericContext {
        public final MoveFactory spillMoveFactory;
        public final RegisterAllocationConfig registerAllocationConfig;

        public AllocationContext(MoveFactory spillMoveFactory, RegisterAllocationConfig registerAllocationConfig) {
            this.spillMoveFactory = spillMoveFactory;
            this.registerAllocationConfig = registerAllocationConfig;
        }
    }

}
