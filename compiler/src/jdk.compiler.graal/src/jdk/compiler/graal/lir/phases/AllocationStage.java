/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.compiler.graal.debug.Assertions;
import jdk.compiler.graal.lir.alloc.AllocationStageVerifier;
import jdk.compiler.graal.lir.stackslotalloc.LSStackSlotAllocator;
import jdk.compiler.graal.lir.stackslotalloc.SimpleStackSlotAllocator;
import jdk.compiler.graal.lir.alloc.lsra.LinearScanPhase;
import jdk.compiler.graal.lir.dfa.MarkBasePointersPhase;
import jdk.compiler.graal.options.OptionValues;

public class AllocationStage extends LIRPhaseSuite<AllocationPhase.AllocationContext> {

    @SuppressWarnings("this-escape")
    public AllocationStage(OptionValues options) {
        appendPhase(new MarkBasePointersPhase());
        appendPhase(new LinearScanPhase());

        // build frame map
        if (LSStackSlotAllocator.Options.LIROptLSStackSlotAllocator.getValue(options)) {
            appendPhase(new LSStackSlotAllocator());
        } else {
            appendPhase(new SimpleStackSlotAllocator());
        }

        if (Assertions.detailedAssertionsEnabled(options)) {
            appendPhase(new AllocationStageVerifier());
        }
    }
}