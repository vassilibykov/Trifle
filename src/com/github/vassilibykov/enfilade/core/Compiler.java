// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;

/**
 * Compiles a {@link FunctionImplementation} into a class with a single static method.
 */
public class Compiler {

    public static final String GENERIC_METHOD_NAME = "generic";
    public static final String SPECIALIZED_METHOD_NAME = "specialized";
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";
    private static final String GENERATED_CODE_PACKAGE = "com.github.vassilibykov.enfilade.core";
    private static final String GENERATED_CLASS_NAME_PREFIX = GENERATED_CODE_PACKAGE + ".$gen$";

    /**
     * The access point: compile a function.
     */
    public static Result compile(FunctionImplementation topLevelFunction) {
        Compiler compiler = new Compiler(topLevelFunction);
        Result result = compiler.compile();
        dumpClassFile("generated", result.bytecode());
        return result;
    }
    private static void dumpClassFile(String name, byte[] bytecode) {
        File classFile = new File(name + ".class");
        try {
            FileOutputStream classStream = new FileOutputStream(classFile);
            classStream.write(bytecode);
            classStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static class Result {
        private final String className;
        private final byte[] bytecode;
        @Nullable private final MethodType specializationType; // set by generateSpecializedMethod()
        private final Map<FunctionImplementation, String> generatedMethodNames;

        private Result(String className, byte[] bytecode, @Nullable MethodType specializationType, Map<FunctionImplementation, String> generatedMethodNames) {
            this.className = className;
            this.bytecode = bytecode;
            this.specializationType = specializationType;
            this.generatedMethodNames = generatedMethodNames;
        }

        public String className() {
            return className;
        }

        public byte[] bytecode() {
            return bytecode;
        }

        @Nullable
        public MethodType specializationType() {
            return specializationType;
        }

        public Map<FunctionImplementation, String> generatedMethodNames() {
            return generatedMethodNames;
        }
    }

    static String internalClassName(Class<?> klass) {
        return internalClassName(klass.getName());
    }

    static String internalClassName(String fqnName) {
        return fqnName.replace('.', '/');
    }

    static String allocateClassName() {
        return GENERATED_CLASS_NAME_PREFIX + serial++;
    }

    private static long serial = 0;

    /*
        Instance
     */

    private final FunctionImplementation topLevelFunction;
    private final String className;
    private final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    @Nullable private MethodType specializationType = null;
    private int genericMethodSerial = 0;
    private final Map<FunctionImplementation, String> generatedMethodNames = new HashMap<>();

    private Compiler(FunctionImplementation topLevelFunction) {
        this.topLevelFunction = topLevelFunction;
        this.className = allocateClassName();
    }

    public Result compile() {
        ExpressionTypeInferencer.inferTypesIn(topLevelFunction);
        ExpressionTypeObserver.analyze(topLevelFunction);
//        NodePrettyPrinter.print(function.body());
        setupClassWriter();
        var name = generateGenericMethod(topLevelFunction);
        generatedMethodNames.put(topLevelFunction, name);
        for (var each : topLevelFunction.closureImplementations()) {
            var closureName = generateGenericMethod(each);
            generatedMethodNames.put(each, closureName);
        }
        if (topLevelFunction.profile.canBeSpecialized()) {
//            generateSpecializedMethod();
        }
        classWriter.visitEnd();
        return new Result(className, classWriter.toByteArray(), specializationType, generatedMethodNames);
    }

    private void setupClassWriter() {
        classWriter.visit(
            Opcodes.V9,
            ACC_PUBLIC | ACC_SUPER | ACC_FINAL,
            internalClassName(className),
            null,
            JAVA_LANG_OBJECT,
            null);
    }

    private String generateGenericMethod(FunctionImplementation closureImpl) {
        var methodName = GENERIC_METHOD_NAME + genericMethodSerial++;
        MethodVisitor methodWriter = classWriter.visitMethod(
            ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
            methodName,
            MethodType.genericMethodType(closureImpl.implementationArity()).toMethodDescriptorString(),
            null, null);
        methodWriter.visitCode();
        CompilerCodeGeneratorGeneric generator = new CompilerCodeGeneratorGeneric(methodWriter);
        JvmType resultType = generator.generate(closureImpl);
        generator.writer.adaptType(resultType, JvmType.REFERENCE);
        methodWriter.visitInsn(Opcodes.ARETURN);
        methodWriter.visitMaxs(-1, -1);
        methodWriter.visitEnd();
        return methodName;
    }

    private void generateSpecializedMethod() {
        specializationType = computeSpecializationType();
        System.out.println("generating a specialized method of type " + specializationType);
        MethodVisitor methodWriter = classWriter.visitMethod(
            ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
            SPECIALIZED_METHOD_NAME,
            specializationType.toMethodDescriptorString(),
            null, null);
        methodWriter.visitCode();
        CompilerCodeGeneratorSpecialized generator = new CompilerCodeGeneratorSpecialized(topLevelFunction, methodWriter);
        generator.generate();
        methodWriter.visitMaxs(-1, -1);
        methodWriter.visitEnd();
    }

    private MethodType computeSpecializationType() {
        Class<?>[] argClasses = topLevelFunction.parameters().stream()
            .map(var -> representativeType(var.observedType()))
            .toArray(Class[]::new);
        return MethodType.methodType(
            representativeType(topLevelFunction.body().observedType()),
            argClasses);
    }

    @SuppressWarnings("unchecked")
    private Class<?> representativeType(ExpressionType observedType) {
        return observedType.jvmType()
            .map(it -> (Class<Object>) it.representativeClass())
            .orElse(Object.class);
    }
}
