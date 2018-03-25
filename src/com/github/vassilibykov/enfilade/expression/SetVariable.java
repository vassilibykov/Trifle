// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.expression;

import org.jetbrains.annotations.NotNull;

public class SetVariable extends ComplexExpression {
    public static SetVariable with(Variable variable, AtomicExpression value) {
        return new SetVariable(variable, value);
    }

    @NotNull private final Variable variable;
    @NotNull private final AtomicExpression value;

    private SetVariable(@NotNull Variable variable, @NotNull AtomicExpression value) {
        this.variable = variable;
        this.value = value;
    }

    public Variable variable() {
        return variable;
    }

    public AtomicExpression value() {
        return value;
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visitSetVariable(this);
    }
}
