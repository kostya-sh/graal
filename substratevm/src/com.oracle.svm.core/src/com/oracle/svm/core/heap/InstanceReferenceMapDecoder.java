/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import jdk.graal.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.util.NonmovableByteArrayReader;
import com.oracle.svm.core.util.coder.NativeCoder;

@DuplicatedInNativeCode
public class InstanceReferenceMapDecoder {
    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean walkOffsetsFromPointer(Pointer baseAddress, NonmovableArray<Byte> referenceMapEncoding, long referenceMapIndex, ObjectReferenceVisitor visitor, Object holderObject) {
        assert ReferenceMapIndex.denotesValidReferenceMap(referenceMapIndex);
        assert referenceMapEncoding.isNonNull();

        Pointer position = NonmovableByteArrayReader.pointerTo(referenceMapEncoding, referenceMapIndex);
        int entryCount = position.readInt(0);
        position = position.add(4);

        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        boolean compressed = ReferenceAccess.singleton().haveCompressedReferences();

        assert entryCount >= 0;
        UnsignedWord sizeOfEntries = Word.unsigned(InstanceReferenceMapEncoder.MAP_ENTRY_SIZE).multiply(entryCount);
        Pointer end = position.add(sizeOfEntries);
        while (position.belowThan(end)) {
            int offset = position.readInt(0);
            position = position.add(4);

            long count = NativeCoder.readU4(position);
            position = position.add(4);

            Pointer objRef = baseAddress.add(offset);
            for (int c = 0; c < count; c++) {
                final boolean visitResult = callVisitor(visitor, holderObject, compressed, objRef);
                if (!visitResult) {
                    return false;
                }
                objRef = objRef.add(referenceSize);
            }
        }
        return true;
    }

    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    @Uninterruptible(reason = "Bridge between uninterruptible and potentially interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static boolean callVisitor(ObjectReferenceVisitor visitor, Object holderObject, boolean compressed, Pointer objRef) {
        return visitor.visitObjectReferenceInline(objRef, 0, compressed, holderObject);
    }
}
