// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.primitives;

import com.github.vassilibykov.enfilade.core.CompilerError;
import com.github.vassilibykov.enfilade.core.EvaluatorNode;
import com.github.vassilibykov.enfilade.core.ExpressionType;
import com.github.vassilibykov.enfilade.core.GhostWriter;
import com.github.vassilibykov.enfilade.core.Primitive1Node;
import com.github.vassilibykov.enfilade.core.JvmType;
import com.github.vassilibykov.enfilade.expression.Expression;
import org.jetbrains.annotations.NotNull;

import static com.github.vassilibykov.enfilade.core.JvmType.INT;
import static com.github.vassilibykov.enfilade.core.JvmType.REFERENCE;
import static org.objectweb.asm.Opcodes.ISUB;

public class Negate extends Primitive1 {
    @Override
    public ExpressionType inferredType(ExpressionType argumentType) {
        return ExpressionType.known(INT);
    }

    @Override
    public Object apply(Object arg) {
        return -((Integer) arg);
    }

    @Override
    public JvmType generate(GhostWriter writer, JvmType argCategory) {
        return argCategory.match(new JvmType.Matcher<JvmType>() {
            public JvmType ifReference() {
                writer
                    .adaptValue(REFERENCE, INT)
                    .loadInt(0)
                    .swap()
                    .asm().visitInsn(ISUB);
                return INT;
            }
            public JvmType ifInt() {
                writer
                    .loadInt(0)
                    .swap()
                    .asm().visitInsn(ISUB);
                return INT;
            }
            public JvmType ifBoolean() {
                throw new CompilerError("NEGATE is not applicable to a boolean");
            }
        });
    }
}
