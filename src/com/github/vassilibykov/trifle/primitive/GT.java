// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.primitive;

import com.github.vassilibykov.trifle.core.ExpressionType;
import com.github.vassilibykov.trifle.core.GhostWriter;
import com.github.vassilibykov.trifle.core.JvmType;
import com.github.vassilibykov.trifle.core.Primitive2Node;
import com.github.vassilibykov.trifle.core.PrimitiveNode;
import com.github.vassilibykov.trifle.core.RuntimeError;
import org.objectweb.asm.Opcodes;

import java.util.Optional;

import static com.github.vassilibykov.trifle.core.JvmType.BOOL;
import static com.github.vassilibykov.trifle.core.JvmType.INT;

/**
 * A greater-than comparison of integers.
 */
public class GT extends Primitive2 implements IfAware {
    @Override
    public ExpressionType inferredType(ExpressionType argument1Type, ExpressionType argument2Type) {
        return ExpressionType.known(BOOL);
    }

    @Override
    public Object apply(Object argument1, Object argument2) {
        try {
            return (Integer) argument1 > (Integer) argument2;
        } catch (ClassCastException e) {
            throw RuntimeError.integerExpected(argument1, argument2);
        }
    }

    @Override
    protected JvmType generateForReferenceReference(GhostWriter writer) {
        writer.invokeStatic(GT.class, "greaterThan", boolean.class, Object.class, Object.class);
        return BOOL;
    }

    @Override
    protected JvmType generateForReferenceInt(GhostWriter writer) {
        writer.invokeStatic(GT.class, "greaterThan", boolean.class, Object.class, int.class);
        return BOOL;
    }

    @Override
    protected JvmType generateForReferenceBoolean(GhostWriter writer) {
        writer.throwError("cannot compare a boolean");
        return BOOL;
    }

    @Override
    protected JvmType generateForIntReference(GhostWriter writer) {
        writer.invokeStatic(GT.class, "greaterThan", boolean.class, int.class, Object.class);
        return BOOL;
    }

    @Override
    protected JvmType generateForIntInt(GhostWriter writer) {
        writer.invokeStatic(GT.class, "greaterThan", boolean.class, int.class, int.class);
        return BOOL;
    }

    @Override
    protected JvmType generateForIntBoolean(GhostWriter writer) {
        writer.throwError("cannot compare a boolean");
        return BOOL;
    }

    @Override
    protected JvmType generateForBooleanReference(GhostWriter writer) {
        writer.throwError("cannot compare a boolean");
        return BOOL;
    }

    @Override
    protected JvmType generateForBooleanInt(GhostWriter writer) {
        writer.throwError("cannot compare a boolean");
        return BOOL;
    }

    @Override
    protected JvmType generateForBooleanBoolean(GhostWriter writer) {
        writer.throwError("cannot compare a boolean");
        return BOOL;
    }

    @Override
    public Optional<OptimizedIfForm> optimizedFormFor(PrimitiveNode ifCondition) {
        var primitive = (Primitive2Node) ifCondition; // cast must succeed
        if (primitive.argument1().specializedType() == INT
            && primitive.argument2().specializedType() == INT)
        {
            return Optional.of(new IfFormOptimizedForInts(primitive, Opcodes.IF_ICMPLE));
        } else {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unused") // called by generated code
    public static boolean greaterThan(Object a, Object b) {
        return (Integer) a > (Integer) b;
    }

    @SuppressWarnings("unused") // called by generated code
    public static boolean greaterThan(Object a, int b) {
        return (Integer) a > b;
    }

    @SuppressWarnings("unused") // called by generated code
    public static boolean greaterThan(int a, Object b) {
        return a > (Integer) b;
    }

    @SuppressWarnings("unused") // called by generated code
    public static boolean greaterThan(int a, int b) {
        return a > b;
    }
}
