/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.replacements.NodeStrideUtil;

public final class ArrayCopyWithConversionsForeignCalls {
    private static final ForeignCallDescriptor STUB_DYNAMIC_STRIDES = foreignCallDescriptor(
                    "arrayCopyWithConversionsDynamicStrides", Object.class, long.class, Object.class, long.class, int.class, int.class);
    /**
     * CAUTION: the ordering here is important: entries 0-9 must match the indices generated by
     * {@link NodeStrideUtil#getDirectStubCallIndex(ValueNode, Stride, Stride)}.
     *
     * @see #getStub(ArrayCopyWithConversionsNode)
     */
    public static final ForeignCallDescriptor[] STUBS = {
                    foreignCallDescriptor("arrayCopyWithConversionsS1S1"),
                    foreignCallDescriptor("arrayCopyWithConversionsS1S2"),
                    foreignCallDescriptor("arrayCopyWithConversionsS1S4"),
                    foreignCallDescriptor("arrayCopyWithConversionsS2S1"),
                    foreignCallDescriptor("arrayCopyWithConversionsS2S2"),
                    foreignCallDescriptor("arrayCopyWithConversionsS2S4"),
                    foreignCallDescriptor("arrayCopyWithConversionsS4S1"),
                    foreignCallDescriptor("arrayCopyWithConversionsS4S2"),
                    foreignCallDescriptor("arrayCopyWithConversionsS4S4"),
                    STUB_DYNAMIC_STRIDES,
    };

    private static ForeignCallDescriptor foreignCallDescriptor(String name) {
        return foreignCallDescriptor(name, Object.class, long.class, Object.class, long.class, int.class);
    }

    private static ForeignCallDescriptor foreignCallDescriptor(String name, Class<?>... argTypes) {
        return new ForeignCallDescriptor(name, void.class, argTypes, HAS_SIDE_EFFECT, ArrayCopyWithConversionsNode.KILLED_LOCATIONS, false, false);
    }

    public static ForeignCallDescriptor getStub(ArrayCopyWithConversionsNode node) {
        int directCallIndex = node.getDirectStubCallIndex();
        if (directCallIndex < 0) {
            return STUB_DYNAMIC_STRIDES;
        }
        return STUBS[directCallIndex];
    }
}