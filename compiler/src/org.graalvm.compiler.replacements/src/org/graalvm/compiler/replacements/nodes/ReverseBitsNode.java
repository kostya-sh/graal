/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_32;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_32;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_32, size = SIZE_32)
public final class ReverseBitsNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<ReverseBitsNode> TYPE = NodeClass.create(ReverseBitsNode.class);

    public ReverseBitsNode(ValueNode value) {
        super(TYPE, value.stamp(NodeView.DEFAULT).unrestricted(), value);
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        if (newStamp instanceof IntegerStamp) {
            IntegerStamp valueStamp = (IntegerStamp) newStamp;
            switch (valueStamp.getBits()) {
                case 32: {
                    long mask = CodeUtil.mask(32);
                    return IntegerStamp.stampForMask(32, Integer.reverse((int) valueStamp.downMask()) & mask, Integer.reverse((int) valueStamp.upMask()) & mask);
                }
                case 64: {
                    return IntegerStamp.stampForMask(64, Long.reverse(valueStamp.downMask()), Long.reverse(valueStamp.upMask()));
                }
                default:
                    throw GraalError.unimplemented("Unsupported bit size " + valueStamp.getBits());
            }
        }
        return stamp(NodeView.DEFAULT);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue instanceof ReverseBitsNode) {
            return ((ReverseBitsNode) forValue).getValue();
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitBitSwap(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}
