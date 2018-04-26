// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.object;

import com.github.vassilibykov.trifle.core.CallDispatcher;
import com.github.vassilibykov.trifle.core.CallNode;
import com.github.vassilibykov.trifle.core.CodeGenerator;
import com.github.vassilibykov.trifle.core.EvaluatorNode;
import com.github.vassilibykov.trifle.core.JvmType;
import com.github.vassilibykov.trifle.core.RuntimeError;

import java.lang.invoke.MethodType;
import java.util.Optional;

class GetFieldDispatcher implements CallDispatcher {

    private final String fieldName;

    GetFieldDispatcher(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public Optional<EvaluatorNode> evaluatorNode() {
        return Optional.empty();
    }

    @Override
    public Object execute(CallNode call, EvaluatorNode.Visitor<Object> interpreter) {
        if (call.arity() != 1) {
            throw RuntimeError.message("invalid call expression"); // TODO should probably use a different exception
        }
        var object = call.parameter(0).accept(interpreter);
        if (object instanceof FixedObject) {
            return ((FixedObject) object).get(fieldName);
        } else {
            throw RuntimeError.message("not an object with fields: " + object);
        }
    }

    @Override
    public JvmType generateCode(CallNode call, CodeGenerator generator) {
        if (call.arity() != 1) {
            throw RuntimeError.message("invalid call expression"); // TODO should probably use a different exception
        }
        generator.generateCode(call.parameter(0));
        generator.writer().invokeDynamic(
            FixedObjectAccessInvokeDynamic.BOOTSTRAP_GET,
            FixedObjectAccessInvokeDynamic.getterName(fieldName),
            MethodType.genericMethodType(1));
        return JvmType.REFERENCE;
    }
}