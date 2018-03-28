// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import org.jetbrains.annotations.NotNull;

/**
 * A function value in a program. Produced by evaluating a lambda expression or
 * by referring to a top-level function.
 */
public class Closure {
    @NotNull /*internal*/ final FunctionImplementation implementation;
    @NotNull /*internal*/ final Object[][] outerFrames;

    Closure(@NotNull FunctionImplementation implementation, @NotNull Object[][] outerFrames) {
        this.implementation = implementation;
        this.outerFrames = outerFrames;
    }

    Closure(@NotNull FunctionImplementation implementation) {
        this.implementation = implementation;
        this.outerFrames = new Object[0][];
    }

    public Object invoke() {
        return implementation.execute(this);
    }

    public Object invoke(Object arg) {
        return implementation.execute(this, arg);
    }

    public Object invoke(Object arg1, Object arg2) {
        return implementation.execute(this, arg1, arg2);
    }
}