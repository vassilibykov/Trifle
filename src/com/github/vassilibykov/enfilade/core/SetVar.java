// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import org.jetbrains.annotations.NotNull;

/**
 * An expression mutating a variable to have a new value. The new value, which
 * is also the value of this expression, must be an atomic expression.
 */
public class SetVar extends ComplexExpression {
    @NotNull private final Var variable;
    @NotNull private final AtomicExpression value;

    SetVar(@NotNull Var variable, @NotNull AtomicExpression value) {
        this.variable = variable;
        this.value = value;
    }

    public Var variable() {
        return variable;
    }

    public AtomicExpression value() {
        return value;
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visitSetVar(this);
    }

    @Override
    public String toString() {
        return "(set! " + variable + " " + value + ")";
    }
}
