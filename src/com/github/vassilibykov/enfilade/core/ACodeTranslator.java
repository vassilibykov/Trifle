// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates an A-normal form expression into the equivalent A-code.
 */
public class ACodeTranslator implements EvaluatorNode.Visitor<Void> {

    public static ACodeInstruction[] translate(EvaluatorNode functionBody) {
        ACodeTranslator translator = new ACodeTranslator();
        functionBody.accept(translator);
        translator.emit(new ACodeInstruction.Return());
        return translator.code.toArray(new ACodeInstruction[translator.code.size()]);
    }

    /*
        Instance
     */

    private final List<ACodeInstruction> code = new ArrayList<>();

    private ACodeTranslator() {}

    @Override
    public Void visitCall0(CallNode.Call0 call) {
        emit(new ACodeInstruction.Call(call));
        return null;
    }

    @Override
    public Void visitCall1(CallNode.Call1 call) {
        emit(new ACodeInstruction.Call(call));
        return null;
    }

    @Override
    public Void visitCall2(CallNode.Call2 call) {
        emit(new ACodeInstruction.Call(call));
        return null;
    }

    @Override
    public Void visitClosure(ClosureNode closure) {
        throw new UnsupportedOperationException("not implemented yet"); // TODO implement
    }

    @Override
    public Void visitConst(ConstNode aConst) {
        emit(new ACodeInstruction.Load(aConst));
        return null;
    }

    @Override
    public Void visitFreeVarReference(FreeVariableReferenceNode varRef) {
        throw new UnsupportedOperationException("not implemented yet"); // TODO implement
    }

    @Override
    public Void visitIf(IfNode anIf) {
        ACodeInstruction.Branch branch = new ACodeInstruction.Branch(anIf.condition(), Integer.MAX_VALUE);
        emit(branch);
        anIf.falseBranch().accept(this);
        ACodeInstruction.Goto theGoto = new ACodeInstruction.Goto(Integer.MAX_VALUE);
        emit(theGoto);
        branch.address = nextInstructionAddress();
        anIf.trueBranch().accept(this);
        theGoto.address = nextInstructionAddress();
        return null;
    }

    @Override
    public Void visitLet(LetNode let) {
        let.initializer().accept(this);
        let.setSetInstructionAddress(nextInstructionAddress());
        emit(new ACodeInstruction.Store(let.variable()));
        let.body().accept(this);
        return null;
    }

    @Override
    public Void visitPrimitive1(Primitive1Node primitive) {
        emit(new ACodeInstruction.Load(primitive));
        return null;
    }

    @Override
    public Void visitPrimitive2(Primitive2Node primitive) {
        emit(new ACodeInstruction.Load(primitive));
        return null;
    }

    @Override
    public Void visitBlock(BlockNode block) {
        EvaluatorNode[] expressions = block.expressions();
        if (expressions.length == 0) {
            emit(new ACodeInstruction.Load(new ConstNode(null)));
            return null;
        }
        for (int i = 0; i < expressions.length - 1; i++) {
            expressions[i].accept(this);
            emit(new ACodeInstruction.Drop());
        }
        expressions[expressions.length - 1].accept(this);
        return null;
    }

    @Override
    public Void visitRet(ReturnNode ret) {
        ret.value().accept(this);
        emit(new ACodeInstruction.Return());
        return null;
    }

    @Override
    public Void visitSetFreeVar(SetFreeVariableNode setFreeVariableNode) {
        throw new UnsupportedOperationException("not implemented yet"); // TODO implement
    }

    @Override
    public Void visitSetVar(SetVariableNode set) {
        set.value().accept(this);
        emit(new ACodeInstruction.Store(set.variable()));
        return null;
    }

    @Override
    public Void visitVarReference(VariableReferenceNode varRef) {
        emit(new ACodeInstruction.Load(varRef));
        return null;
    }

    private void emit(ACodeInstruction instruction) {
        code.add(instruction);
    }

    private int nextInstructionAddress() {
        return code.size();
    }
}