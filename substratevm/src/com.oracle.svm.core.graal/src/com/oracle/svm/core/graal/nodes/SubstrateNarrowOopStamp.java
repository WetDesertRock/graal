/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.graal.nodes;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.nodes.type.NarrowOopStamp;

import com.oracle.svm.core.graal.meta.SubstrateMemoryAccessProvider;
import com.oracle.svm.core.meta.CompressedNullConstant;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

public final class SubstrateNarrowOopStamp extends NarrowOopStamp {
    private SubstrateNarrowOopStamp(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull, CompressEncoding encoding) {
        super(type, exactType, nonNull, alwaysNull, encoding);
    }

    @Override
    protected AbstractObjectStamp copyWith(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull) {
        return new SubstrateNarrowOopStamp(type, exactType, nonNull, alwaysNull, getEncoding());
    }

    public static AbstractObjectStamp compressed(AbstractObjectStamp stamp, CompressEncoding encoding) {
        return new SubstrateNarrowOopStamp(stamp.type(), stamp.isExactType(), stamp.nonNull(), stamp.alwaysNull(), encoding);
    }

    @Override
    public Constant readConstant(MemoryAccessProvider memoryAccessProvider, Constant base, long displacement) {
        SubstrateMemoryAccessProvider provider = (SubstrateMemoryAccessProvider) memoryAccessProvider;
        SubstrateObjectConstant constant = (SubstrateObjectConstant) provider.readNarrowObjectConstant(base, displacement);
        assert constant != null && constant.isCompressed();
        return constant;
    }

    @Override
    public JavaConstant asConstant() {
        return alwaysNull() ? CompressedNullConstant.COMPRESSED_NULL : null;
    }

    @Override
    public boolean isCompatible(Constant other) {
        return other instanceof SubstrateObjectConstant ? ((SubstrateObjectConstant) other).isCompressed() : true;
    }

    public static Stamp mkStamp(CompressionOp op, Stamp input, CompressEncoding encoding) {
        switch (op) {
            case Compress:
                if (input instanceof ObjectStamp) {
                    return compressed((ObjectStamp) input, encoding);
                }
                break;
            case Uncompress:
                if (input instanceof NarrowOopStamp) {
                    NarrowOopStamp inputStamp = (NarrowOopStamp) input;
                    assert encoding.equals(inputStamp.getEncoding());
                    return inputStamp.uncompressed();
                }
                break;
        }
        throw GraalError.shouldNotReachHere("Unexpected input stamp " + input);
    }
}
