// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import com.github.vassilibykov.enfilade.primitives.IfAware;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.github.vassilibykov.enfilade.core.JvmType.BOOL;
import static com.github.vassilibykov.enfilade.core.JvmType.REFERENCE;
import static com.github.vassilibykov.enfilade.core.JvmType.VOID;

/**
 * Generates the "main" executable representation of a function.
 *
 * @see RecoveryCodeGenerator
 */
class MethodCodeGenerator implements EvaluatorNode.Visitor<JvmType> {

    /**
     * Captures the information about an SPE handler that needs to be generated
     * once we are done with the method proper.
     */
    private static class SquarePegHandler {
        /**
         * The label to be positioned at the entry into the handler code.
         */
        private final Label handlerStart;
        /**
         * Local variables which are live upon firing the handler. The list does
         * NOT include any parameters, since they are by definition always live.
         */
        private final List<AbstractVariable> liveLocals;
        private final Label recoverySiteLabel;

        private SquarePegHandler(Label handlerStart, List<AbstractVariable> liveLocals) {
            this.handlerStart = handlerStart;
            this.liveLocals = liveLocals;
            this.recoverySiteLabel = new Label();
        }
    }

    /*
        Instance
     */

    private final FunctionImplementation function;
    private final GhostWriter writer;
    private final List<AbstractVariable> liveLocals = new ArrayList<>();
    private final List<SquarePegHandler> squarePegHandlers = new ArrayList<>();

    MethodCodeGenerator(FunctionImplementation function, MethodVisitor writer) {
        this.function = function;
        this.writer = new GhostWriter(writer);
    }

    public GhostWriter writer() {
        return writer;
    }

    void generate() {
        generatePrologue();
        var expressionType = function.body().accept(this);
        bridgeValue(expressionType, function.specializedReturnType);
        writer.ret(function.specializedReturnType);
        if (!squarePegHandlers.isEmpty()) {
            generateRecoveryHandlers();
            generateRecoveryCode();
        }
    }

    /**
     * For any function parameter which is boxed, replace the value passed in
     * with a box containing that value. This only concerns declared parameters;
     * any copied outer context values have already been boxed by the caller.
     */
    private void generatePrologue() {
        for (var each : function.declaredParameters()) {
            if (each.isBoxed()) {
                var paramType = each.specializedType();
                int index = each.index();
                writer
                    .loadLocal(paramType, index)
                    .initBoxedVariable(paramType, index);
            }
        }
    }

    private void generateRecoveryHandlers() {
        squarePegHandlers.forEach(this::generateSquarePegHandler);
    }

    private void generateRecoveryCode() {
        var generator = new RecoveryCodeGenerator(function, writer);
        var resultType = generator.generate();
        bridgeValue(resultType, function.specializedReturnType);
        writer.ret(function.specializedReturnType);
    }

    /*
        An overview of how values produced by visitor methods are handled.

        I believe the following are fundamental invariants. If a method
        doesn't do they say it should, or does something they say it doesn't
        have to, the method is probably wrong.

        A visitor method always returns the JvmType left on the stack by the
        code the method generated.

        A visitor is free to generate a value of any type it wants; it is the
        visitor caller's (the parent expression visitor's) responsibility to
        bridge the value to the type it needs for its own code.

        Bridging may cause SPEs if it's type-narrowing. Thus a visitor method
        which performs bridging must make recovery provisions if it uses the
        value itself. Examples are the visitors of 'let', 'letrec', 'set!'
        and 'return'.

        If a complex subexpression code is generated and bridged in the tail
        position of the visitor code so the visitor does not use it itself,
        like the visitor of 'if' when it bridges each branch value to the
        union type of both branches, there is no need to handle a possible SPE,
        as the expression value is already produced and the SPE handler of
        the complex expression that accepts the value will take care of it.
     */

    @Override
    public JvmType visitCall0(CallNode.Call0 call) {
        var functionType = call.function().accept(this);
        writer.ensureValue(functionType, REFERENCE);
        var returnType = call.specializedType();
        var callSiteSignature = MethodType.methodType(returnType.representativeClass(), Object.class);
        writer.invokeDynamic(ClosureInvokeDynamic.BOOTSTRAP, "call0", callSiteSignature);
        return returnType;
    }

    @Override
    public JvmType visitCall1(CallNode.Call1 call) {
        var functionType = call.function().accept(this);
        writer.ensureValue(functionType, REFERENCE);
        var argType = call.arg().accept(this);
        var returnType = call.specializedType();
        var callSiteSignature = MethodType.methodType(
            returnType.representativeClass(),
            Object.class, // the closure being called
            argType.representativeClass());
        writer.invokeDynamic(ClosureInvokeDynamic.BOOTSTRAP, "call1", callSiteSignature);
        return returnType;
    }

    @Override
    public JvmType visitCall2(CallNode.Call2 call) {
        var functionType = call.function().accept(this);
        writer.ensureValue(functionType, REFERENCE);
        var arg1Type = call.arg1().specializedType();
        var arg2Type = call.arg2().specializedType();
        var returnType = call.specializedType();
        var callSiteSignature = MethodType.methodType(
            returnType.representativeClass(),
            Object.class, // really a Closure, but we type it as Object to catch errors locally
            arg1Type.representativeClass(),
            arg2Type.representativeClass());
        writer.invokeDynamic(ClosureInvokeDynamic.BOOTSTRAP, "call2", callSiteSignature);
        return returnType;
    }

    @Override
    public JvmType visitClosure(ClosureNode closure) {
        var copiedOuterVariables = closure.copiedOuterVariables;
        writer.newObjectArray(copiedOuterVariables.size());
        for (int i = 0; i < copiedOuterVariables.size(); i++) {
            var variable = copiedOuterVariables.get(i);
            writer
                .dup()
                .loadInt(i);
            if (variable.isBoxed()) {
                writer.loadLocal(REFERENCE, variable.index());
            } else {
                JvmType variableType = variable.specializedType();
                writer
                    .loadLocal(variableType, variable.index())
                    .adaptValue(variableType, REFERENCE);
            }
            writer.asm().visitInsn(Opcodes.AASTORE);
        }
        writer
            .loadInt(closure.function().id())
            .invokeStatic(Closure.class, "create", Closure.class, Object[].class, int.class);
        return REFERENCE;
    }

    @Override
    public JvmType visitConstant(ConstantNode aConst) {
        Object value = aConst.value();
        if (value instanceof Integer) {
            writer.loadInt((Integer) value);
        } else if (value instanceof String) {
            writer.loadString((String) value);
        } else if (value == null) {
            writer.loadNull();
        } else if (value instanceof Boolean) {
            writer.loadInt((Boolean) value ? 1 : 0);
        } else {
            throw new CompilerError("unexpected const value: " + value);
        }
        return aConst.specializedType();
    }

    @Override
    public JvmType visitDirectCall0(CallNode.DirectCall0 call) {
        var returnType = call.specializedType();
        var callSiteSignature = MethodType.methodType(
            returnType.representativeClass());
        writer.invokeDynamic(ConstantFunctionInvokeDynamic.BOOTSTRAP, "call0", callSiteSignature, call.target().id());
        return returnType;
    }

    @Override
    public JvmType visitDirectCall1(CallNode.DirectCall1 call) {
        var argType = call.arg().accept(this);
        var returnType = call.specializedType();
        var callSiteSignature = MethodType.methodType(
            returnType.representativeClass(),
            argType.representativeClass());
        writer.invokeDynamic(ConstantFunctionInvokeDynamic.BOOTSTRAP, "call1", callSiteSignature, call.target().id());
        return returnType;
    }

    @Override
    public JvmType visitDirectCall2(CallNode.DirectCall2 call) {
        var arg1Type = call.arg1().accept(this);
        var arg2Type = call.arg2().accept(this);
        var returnType = call.specializedType();
        var callSiteSignature = MethodType.methodType(
            returnType.representativeClass(),
            arg1Type.representativeClass(),
            arg2Type.representativeClass());
        writer.invokeDynamic(ConstantFunctionInvokeDynamic.BOOTSTRAP, "call2", callSiteSignature, call.target().id());
        return returnType;
    }

    @Override
    public JvmType visitGetVar(GetVariableNode varRef) {
        var variable = varRef.variable();
        var varType = variable.specializedType();
        if (variable.isBoxed()) {
            writer
                .loadLocal(REFERENCE, variable.index())
                .unboxValue(varType);
        } else {
            writer.loadLocal(varType, variable.index());
        }
        return varType;
    }

    @Override
    public JvmType visitIf(IfNode anIf) {
        var trueBranch = anIf.trueBranch();
        var falseBranch = anIf.falseBranch();
        var resultType = anIf.specializedType();
        // Generate an optimized 'if' form, if possible
        if (anIf.condition() instanceof PrimitiveNode) {
            var primitiveCall = (PrimitiveNode) anIf.condition();
            if (primitiveCall.implementation() instanceof IfAware) {
                var generator = (IfAware) primitiveCall.implementation();
                var maybeOptimized = generator.optimizedFormFor(primitiveCall);
                if (maybeOptimized.isPresent()) {
                    var optimized = maybeOptimized.get();
                    optimized.loadArguments(each -> each.accept(this));
                    writer.withLabelAtEnd(end -> {
                        writer.withLabelAtEnd(elseStart -> {
                            writer.asm().visitJumpInsn(optimized.jumpInstruction(), elseStart);
                            var valueType = trueBranch.accept(this);
                            bridgeValue(valueType, resultType); // in tail position
                            writer.jump(end);
                        });
                        var valueType = falseBranch.accept(this);
                        bridgeValue(valueType, resultType); // in tail position
                    });
                    return resultType;
                }
            }
        }
        // General 'if' form
        var conditionType = anIf.condition().accept(this);
        writer.ensureValue(conditionType, BOOL);
        writer.ifThenElse(
            () -> {
                var valueType = trueBranch.accept(this);
                bridgeValue(valueType, resultType); // in tail position
            },
            () -> {
                var valueType = falseBranch.accept(this);
                bridgeValue(valueType, resultType); // in tail position
            }
        );
        return resultType;
    }

    @Override
    public JvmType visitLet(LetNode let) {
        VariableDefinition variable = let.variable();
        JvmType varType = variable.specializedType();
        withSquarePegRecovery(let, () -> {
            var initType = let.initializer().accept(this);
            bridgeValue(initType, varType);
        });
        if (variable.isBoxed()) {
            writer.initBoxedVariable(varType, variable.index());
        } else {
            writer.storeLocal(varType, variable.index());
        }
        liveLocals.add(variable);
        var bodyType = let.body().accept(this);
        liveLocals.remove(variable);
        return bodyType;
    }

    @Override
    public JvmType visitLetrec(LetrecNode letrec) {
        VariableDefinition variable = letrec.variable();
        JvmType varType = variable.specializedType();
        if (variable.isBoxed()) {
            writer
                .loadDefaultValue(varType)
                .initBoxedVariable(varType, variable.index());
        } else {
            writer
                .loadDefaultValue(varType)
                .storeLocal(varType, variable.index());
        }
        liveLocals.add(variable);
        withSquarePegRecovery(letrec, () -> {
            var initType = letrec.initializer().accept(this);
            bridgeValue(initType, varType);
        });
        if (variable.isBoxed()) {
            writer.storeBoxedVariable(varType, variable.index());
        } else {
            writer.storeLocal(varType, variable.index());
        }
        var bodyType = letrec.body().accept(this);
        liveLocals.remove(variable);
        return bodyType;
    }

    @Override
    public JvmType visitPrimitive1(Primitive1Node primitive) {
        var argType = primitive.argument().accept(this);
        return primitive.implementation().generate(writer, argType);
    }

    @Override
    public JvmType visitPrimitive2(Primitive2Node primitive) {
        var arg1Type = primitive.argument1().accept(this);
        var arg2Type = primitive.argument2().accept(this);
        return primitive.implementation().generate(writer, arg1Type, arg2Type);
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
            expressions[i].accept(this);
            writer.pop();
        }
        return expressions[i].accept(this);
    }

    @Override
    public JvmType visitReturn(ReturnNode returnNode) {
        /* Return causes a non-local control transfer, so the bridging of the return value
           is not in the tail position and must have an SPE handler established.
           The return value is atomic and in principle there is no need to place
           its code within an SPE handler. But just in case we might lift that
           restriction and forget to modify the code here accordingly, we treat
           value() as if it were complex already. */
        withSquarePegRecovery(returnNode, () -> {
            var returnType = returnNode.value().accept(this);
            bridgeValue(returnType, function.specializedReturnType);
        });
        writer.ret(function.specializedReturnType);
        return VOID;
    }

    @Override
    public JvmType visitSetVar(SetVariableNode set) {
        var var = set.variable();
        var varType = var.specializedType();
        withSquarePegRecovery(set, () -> {
            var valueType = set.value().accept(this);
            bridgeValue(valueType, varType);
        });
        writer.dup(); // the duplicate is left on the stack as the set expression value
        if (var.isBoxed()) {
            writer.storeBoxedVariable(varType, var.index());
        } else {
            writer.storeLocal(varType, var.index());
        }
        return varType;
    }

    @Override
    public JvmType visitConstantFunction(DirectFunctionNode constFunction) {
        int id = constFunction.id();
        writer
            .loadInt(id)
            .invokeStatic(CallableRegistry.class, "lookupAndMakeClosure", Closure.class, int.class);
        return REFERENCE;
    }

    /**
     * Assuming that a value of type 'from' is on the stack in the context whose
     * continuation expects a value of type 'to', generate code that will ensure
     * the continuation will successfully receive the value.
     *
     * <p>If the from/to pair of types is such that a value of 'from' cannot in
     * the be converted to a value of 'to', for example {@code reference -> int}
     * when the reference is not to an {@code Integer}, the generated code will
     * throw an exception to complete the evaluation in recovery mode.
     *
     * <p>If the 'to' type is VOID, that means the value will be discarded by
     * the continuation, so it doesn't matter what it is.
     *
     * <p>This is different from {@link GhostWriter#adaptValue(JvmType,
     * JvmType)}. The latter performs wrapping and unwrapping of values,
     * assuming that in a conversion between a primitive and a reference type,
     * the reference type is a valid wrapper value for the primitive. In a
     * conversion from a reference to an int, the reference can value never be
     * anything other than {@code Integer}. This is true no matter if the user
     * program is correct or not. A violation of this expectation is a sign of
     * an internal error in the compiler.
     *
     * <p>In contrast, in bridging a reference to an int it's normal for the
     * reference value to not be an {@code Integer}. In that case it should be
     * packaged up and thrown as a {@link SquarePegException}.
     *
     */
    private void bridgeValue(JvmType from, JvmType to) {
        from.match(new JvmType.VoidMatcher() {
            public void ifReference() {
                to.match(new JvmType.VoidMatcher() {
                    public void ifReference() { }
                    public void ifInt() { writer.unwrapIntegerOr(writer::throwSquarePegException); }
                    public void ifBoolean() { writer.unwrapBooleanOr(writer::throwSquarePegException); }
                    public void ifVoid() { }
                });
            }
            public void ifInt() {
                to.match(new JvmType.VoidMatcher() {
                    public void ifReference() { writer.wrapInteger(); }
                    public void ifInt() { }
                    public void ifBoolean() { writer.wrapInteger().throwSquarePegException(); }
                    public void ifVoid() { }
                });
            }
            public void ifBoolean() {
                to.match(new JvmType.VoidMatcher() {
                    public void ifReference() { writer.wrapBoolean(); }
                    public void ifInt() { writer.wrapBoolean().throwSquarePegException(); }
                    public void ifBoolean() { }
                    public void ifVoid() { }
                });
            }
            public void ifVoid() {
                // occurs in the middle of blocks and in return statements; nothing needs to be done
            }
        });
    }

    private void withSquarePegRecovery(RecoverySite requestor, Runnable generate) {
        Label handlerStart = new Label();
        SquarePegHandler handler = new SquarePegHandler(
            handlerStart,
            new ArrayList<>(liveLocals));
        requestor.setRecoverySiteLabel(handler.recoverySiteLabel);
        squarePegHandlers.add(handler);
        writer.withLabelsAround((begin, end) -> {
            generate.run();
            writer.handleSquarePegException(begin, end, handlerStart);
        });
    }

    /**
     * Generate the code of an exception handler for recovering from an SPE. The
     * handler should unwrap the SPE currently on the stack and unspecialize any
     * specialized live locals, then jump to the continuation location in the
     * generic code. The unwrapped value of the SPE should be the only value on
     * the stack when jumping.
     */
    private void generateSquarePegHandler(SquarePegHandler handler) {
        writer.asm().visitLabel(handler.handlerStart);
        // stack: SquarePegException
        writer.unwrapSPE();
        // stack: continuation value
        Stream.concat(Stream.of(function.allParameters()), handler.liveLocals.stream()).forEach(var -> {
            var varType = var.specializedType();
            if (!var.isBoxed() && varType != REFERENCE) {
                writer
                    .loadLocal(varType, var.index())
                    .adaptValue(varType, REFERENCE)
                    .storeLocal(REFERENCE, var.index());
            }
        });
        writer.jump(handler.recoverySiteLabel);
    }
}