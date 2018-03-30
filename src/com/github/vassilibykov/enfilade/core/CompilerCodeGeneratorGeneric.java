// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.MethodVisitor;

import java.lang.invoke.MethodType;

import static com.github.vassilibykov.enfilade.core.JvmType.BOOL;
import static com.github.vassilibykov.enfilade.core.JvmType.INT;
import static com.github.vassilibykov.enfilade.core.JvmType.REFERENCE;

/**
 * A code generator producing code for the generic version of a function.
 * The result returned by each visitor method is the type category of the
 * value of the the subexpression compiled by the method.
 */
class CompilerCodeGeneratorGeneric implements EvaluatorNode.Visitor<JvmType> {
    protected final GhostWriter writer;

    CompilerCodeGeneratorGeneric(MethodVisitor visitor) {
        this.writer = new GhostWriter(visitor);
    }

    JvmType generate(FunctionImplementation function) {
        generatePrologue(function);
        return function.body().accept(this);
    }

    private void generatePrologue(FunctionImplementation function) {
        for (var each : function.parameters()) {
            if (each.isBoxed()) {
                int index = each.genericIndex();
                writer
                    .loadLocal(REFERENCE, index)
                    .initBoxedVariable(index);
            }
        }
    }

    @Override
    public JvmType visitCall0(CallNode.Call0 call) {
        if (call.function() instanceof ConstantFunctionNode) {
            return generateConstantFunctionCall0(call, (ConstantFunctionNode) call.function());
        }
        call.function().accept(this); // puts a value on the stack that must be a closure
        var type = MethodType.genericMethodType(1);
        writer.invokeDynamic(
            ClosureInvokeDynamic.BOOTSTRAP,
            "call0",
            type);
        return REFERENCE;
    }

    private JvmType generateConstantFunctionCall0(CallNode.Call0 call, ConstantFunctionNode function) {
        // For a constant call, the closure is not pushed on the stack.
        // Its ID is instead encoded in the invokedynamic instruction.
        var type = MethodType.genericMethodType(0);
        writer.invokeDynamic(
            ConstantFunctionInvokeDynamic.BOOTSTRAP,
            "call0",
            type,
            function.id());
        return REFERENCE;
    }

    @Override
    public JvmType visitCall1(CallNode.Call1 call) {
        if (call.function() instanceof ConstantFunctionNode) {
            return generateConstantFunctionCall1(call, (ConstantFunctionNode) call.function());
        }
        call.function().accept(this); // puts a value on the stack that must be a closure
        var type = MethodType.genericMethodType(2);
        var argType = call.arg().accept(this);
        writer.adaptType(argType, REFERENCE);
        writer.invokeDynamic(
            ClosureInvokeDynamic.BOOTSTRAP,
            "call1",
            type);
        return REFERENCE;
    }

    private JvmType generateConstantFunctionCall1(CallNode.Call1 call, ConstantFunctionNode function) {
        var type = MethodType.genericMethodType(1);
        var argType = call.arg().accept(this);
        writer.adaptType(argType, REFERENCE);
        writer.invokeDynamic(
            ConstantFunctionInvokeDynamic.BOOTSTRAP,
            "call1",
            type,
            function.id());
        return REFERENCE;
    }

    @Override
    public JvmType visitCall2(CallNode.Call2 call) {
        if (call.function() instanceof ConstantFunctionNode) {
            return generateConstantFunctionCall2(call, (ConstantFunctionNode) call.function());
        }
        call.function().accept(this); // puts a value on the stack that must be a closure
        var type = MethodType.genericMethodType(3);
        var arg1Type = call.arg1().accept(this);
        writer.adaptType(arg1Type, REFERENCE);
        var arg2Type = call.arg2().accept(this);
        writer.adaptType(arg2Type, REFERENCE);
        writer.invokeDynamic(
            ClosureInvokeDynamic.BOOTSTRAP,
            "call2",
            type);
        return REFERENCE;
    }

    private JvmType generateConstantFunctionCall2(CallNode.Call2 call, ConstantFunctionNode function) {
        var type = MethodType.genericMethodType(1);
        var arg1Type = call.arg1().accept(this);
        writer.adaptType(arg1Type, REFERENCE);
        var arg2Type = call.arg2().accept(this);
        writer.adaptType(arg2Type, REFERENCE);
        writer.invokeDynamic(
            ConstantFunctionInvokeDynamic.BOOTSTRAP,
            "call1",
            type,
            function.id());
        return REFERENCE;
    }

    @Override
    public JvmType visitClosure(ClosureNode closure) {
        var indicesToCopy = closure.indicesToCopy;
        writer.newObjectArray(indicesToCopy.length);
        for (int i = 0; i < indicesToCopy.length; i++) {
            writer
                .dup()
                .loadInt(i)
                .loadLocal(REFERENCE, indicesToCopy[i]);
            writer.asm().visitInsn(Opcodes.AASTORE);
        }
        writer
            .loadInt(FunctionRegistry.INSTANCE.lookup(closure.function()))
            .invokeStatic(Closure.class, "create", Closure.class, Object[].class, int.class);
        return REFERENCE;
    }

    @Override
    public JvmType visitConst(ConstNode aConst) {
        Object value = aConst.value();
        if (value instanceof Integer) {
            writer.loadInt((Integer) value);
            return INT;
        } else if (value instanceof String) {
            writer.loadString((String) value);
            return REFERENCE;
        } else if (value == null) {
            writer.loadNull();
            return REFERENCE;
        } else {
            throw new CompilerError("unexpected const value: " + value);
        }
    }

    @Override
    public JvmType visitGetVar(GetVariableNode getVar) {
        var variable = getVar.variable();
        writer.loadLocal(REFERENCE, variable.genericIndex());
        if (variable.isBoxed()) writer.extractBoxedVariable();
        return REFERENCE;
    }

    @Override
    public JvmType visitIf(IfNode anIf) {
        JvmType testType = anIf.condition().accept(this);
        writer.adaptType(testType, BOOL);
        writer.ifThenElse(
            () -> {
                JvmType type = anIf.trueBranch().accept(this);
                writer.adaptType(type, REFERENCE);
            },
            () -> {
                JvmType type = anIf.falseBranch().accept(this);
                writer.adaptType(type, REFERENCE);
            }
        );
        return REFERENCE;
    }

    @Override
    public JvmType visitLet(LetNode let) {
        var variable = let.variable();
        if (variable.isBoxed() && let.isLetrec()) {
            writer
                .loadNull()
                .initBoxedVariable(variable.genericIndex);
        }
        var initType = let.initializer().accept(this);
        writer.adaptType(initType, REFERENCE);
        if (variable.isBoxed()) {
            if (let.isLetrec()) {
                writer.storeBoxedVariable(variable.genericIndex());
            } else {
                writer.initBoxedVariable(variable.genericIndex());
            }
        } else {
            writer.storeLocal(REFERENCE, variable.genericIndex());
        }
        return let.body().accept(this);
    }

    @Override
    public JvmType visitPrimitive1(Primitive1Node primitive1) {
        JvmType argType = primitive1.argument().accept(this);
        return primitive1.generate(writer, argType);
    }

    @Override
    public JvmType visitPrimitive2(Primitive2Node primitive2) {
        JvmType arg1Type =  primitive2.argument1().accept(this);
        JvmType arg2Type = primitive2.argument2().accept(this);
        return primitive2.generate(writer, arg1Type, arg2Type);
    }

    @Override
    public JvmType visitBlock(BlockNode block) {
        EvaluatorNode[] expressions = block.expressions();
        if (expressions.length == 0) {
            writer.loadNull();
            return REFERENCE;
        }
        int i;
        for (i = 0; i < expressions.length - 1; i++) {
            EvaluatorNode expr = expressions[i];
            expr.accept(this);
            writer.pop();
        }
        return expressions[i].accept(this);
    }

    @Override
    public JvmType visitRet(ReturnNode ret) {
        throw new UnsupportedOperationException("not implemented yet"); // TODO implement
    }

    @Override
    public JvmType visitSetVar(SetVariableNode setVar) {
        var variable = setVar.variable();
        var varType = setVar.value().accept(this);
        writer
            .adaptType(varType, REFERENCE)
            .dup();
        if (variable.isBoxed()) {
            writer.storeBoxedVariable(variable.genericIndex());
        } else {
            writer.storeLocal(REFERENCE, variable.genericIndex());
        }
        return REFERENCE;
    }

    @Override
    public JvmType visitConstantFunction(ConstantFunctionNode constFunction) {
        var closure = constFunction.closure();
        int id = ConstantFunctionNode.lookup(closure);
        writer
            .loadInt(id)
            .invokeStatic(ConstantFunctionNode.class, "lookup", Closure.class, int.class);
        return REFERENCE;
    }
}
