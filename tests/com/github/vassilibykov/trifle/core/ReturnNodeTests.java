// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.core;

import org.junit.Before;
import org.junit.Test;

import static com.github.vassilibykov.trifle.core.JvmType.INT;
import static com.github.vassilibykov.trifle.core.JvmType.REFERENCE;
import static com.github.vassilibykov.trifle.core.JvmType.VOID;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.block;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.const_;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.lambda;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.ret;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("ConstantConditions")
public class ReturnNodeTests {

    private UserFunction closure;
    private FunctionImplementation function;
    private ReturnNode node;

    @Before
    public void setUp() throws Exception {
        var topLevel = new Library();
        topLevel.define("test",
            lambda(() -> block(
                ret(const_(42)),
                const_("hello"))));
        closure = topLevel.get("test");
        function = closure.implementation();
        var block = (BlockNode) function.body();
        node = (ReturnNode) block.expressions()[0];
    }

    @Test
    public void profiledInterpretedEvaluation() {
        assertEquals(42, closure.invoke());
    }

    @Test
    public void simpleInterpretedEvaluation() {
        function.useSimpleInterpreter();
        assertEquals(42, closure.invoke());
    }

    @Test
    public void inferredType() {
        function.forceCompile();
        assertEquals(VOID, node.inferredType().jvmType().get());
        assertEquals(REFERENCE, function.body().inferredType().jvmType().get());
    }

    @Test
    public void specializedType() {
        closure.invoke();
        function.forceCompile();
        assertEquals(INT, function.specializedReturnType());
        assertEquals(REFERENCE, function.body().specializedType());
    }

    @Test
    public void compiledEvaluation() {
        closure.invoke();
        function.forceCompile();
        assertEquals(42, closure.invoke());
    }
}
