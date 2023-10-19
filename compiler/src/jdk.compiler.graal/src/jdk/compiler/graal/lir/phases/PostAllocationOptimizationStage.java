/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.lir.phases;

import jdk.compiler.graal.lir.ComputeCodeEmissionOrder;
import jdk.compiler.graal.lir.ControlFlowOptimizer;
import jdk.compiler.graal.lir.EdgeMoveOptimizer;
import jdk.compiler.graal.lir.NullCheckOptimizer;
import jdk.compiler.graal.lir.RedundantMoveElimination;
import jdk.compiler.graal.lir.profiling.MethodProfilingPhase;
import jdk.compiler.graal.lir.profiling.MoveProfilingPhase;
import jdk.compiler.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import jdk.compiler.graal.options.NestedBooleanOptionKey;
import jdk.compiler.graal.options.Option;
import jdk.compiler.graal.options.OptionKey;
import jdk.compiler.graal.options.OptionType;
import jdk.compiler.graal.options.OptionValues;

public class PostAllocationOptimizationStage extends LIRPhaseSuite<PostAllocationOptimizationContext> {
    public static class Options {
        // @formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptEdgeMoveOptimizer = new NestedBooleanOptionKey(LIRPhase.Options.LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptControlFlowOptimizer = new NestedBooleanOptionKey(LIRPhase.Options.LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptRedundantMoveElimination = new NestedBooleanOptionKey(LIRPhase.Options.LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptNullCheckOptimizer = new NestedBooleanOptionKey(LIRPhase.Options.LIROptimization, true);
        @Option(help = "Enables profiling of move types on LIR level. " +
                       "Move types are for example stores (register to stack), " +
                       "constant loads (constant to register) or copies (register to register).", type = OptionType.Debug)
        public static final OptionKey<Boolean> LIRProfileMoves = new OptionKey<>(false);
        @Option(help = "Enables profiling of methods.", type = OptionType.Debug)
        public static final OptionKey<Boolean> LIRProfileMethods = new OptionKey<>(false);
        // @formatter:on
    }

    @SuppressWarnings("this-escape")
    public PostAllocationOptimizationStage(OptionValues options) {
        if (Options.LIROptEdgeMoveOptimizer.getValue(options)) {
            appendPhase(new EdgeMoveOptimizer());
        }
        if (Options.LIROptRedundantMoveElimination.getValue(options)) {
            appendPhase(new RedundantMoveElimination());
        }
        if (Options.LIROptNullCheckOptimizer.getValue(options)) {
            appendPhase(new NullCheckOptimizer());
        }
        // Control flow optimization looks for empty blocks, so for full effect it should run after
        // any other optimizations that may eliminate instructions.
        if (Options.LIROptControlFlowOptimizer.getValue(options)) {
            appendPhase(new ControlFlowOptimizer());
        }
        if (Options.LIRProfileMoves.getValue(options)) {
            appendPhase(new MoveProfilingPhase());
        }
        if (Options.LIRProfileMethods.getValue(options)) {
            appendPhase(new MethodProfilingPhase());
        }
        if (!ComputeCodeEmissionOrder.Options.EarlyCodeEmissionOrder.getValue(options)) {
            appendPhase(new ComputeCodeEmissionOrder());
        }
    }
}