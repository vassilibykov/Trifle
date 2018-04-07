// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A function value in a program. Produced by evaluating a lambda expression or
 * by referencing a top-level function.
 *
 * <p>See {@code doc/closure-calls.md} for a design overview of closure invocations.
 */
public class Closure {
    @SuppressWarnings("unused") // called by generated code
    public static Closure create(Object[] copiedValues, int functionId) {
        return new Closure(
            CallableRegistry.INSTANCE.lookupFunctionImplementation(functionId),
            copiedValues);
    }

    public static Closure with(FunctionImplementation topLevelFunctionImplementation) {
        return new Closure(topLevelFunctionImplementation, new Object[0]);
    }

    @NotNull /*internal*/ final FunctionImplementation implementation;
    private final Object[] copiedValues;
    private final MethodHandle genericInvoker;
    @Nullable private MethodType specializedInvokerType;
    @Nullable private MethodHandle specializedInvoker;

    Closure(@NotNull FunctionImplementation implementation, @NotNull Object[] copiedValues) {
        this.implementation = implementation;
        this.copiedValues = copiedValues;
        // callSiteInvoker type: (synthetic:Object* declared:Object*) -> Object
        // invoker type: (declared:Object*) -> Object
        this.genericInvoker = MethodHandles.insertArguments(implementation.callSiteInvoker, 0, copiedValues);
    }

    boolean hasCopiedValues() {
        return copiedValues.length != 0;
    }

    /**
     * A method handle invoking which evaluates the closure. Its type is {@code
     * (Object{n}) -> Object}, where {@code n} is the closure arity. Call sites
     * which link their inline caches to this function should use {@link
     * #optimalInvoker(MethodType)} instead because that will result in a
     * more efficient call pipeline, both for specialized and generic forms.
     */
    MethodHandle genericInvoker() {
        return genericInvoker;
    }

    /**
     * Return an invoker of the specified type, where the type only considers
     * the function's declared parameters. It does not include the leading
     * closure as do the types of invokedynamic call sites, nor the types of
     * synthetic parameters. The result uses the implementation's specialized
     * compiled form, if possible.
     *
     * <p>This method should normally be called when linking to a compiled
     * function so we can use more efficient direct links to its compiled
     * forms, whether specialized or generic. Otherwise, it will link to
     * the dynamic invoker of the implementation's core call site, which
     * might later be doing a specialization check on every call.
     */
    MethodHandle optimalInvoker(MethodType requiredType) {
        if (specializedInvokerType == requiredType) return specializedInvoker;
        if (requiredType.parameterCount() != implementation.declarationArity()) {
            throw new IllegalArgumentException();
        }
        if (requiredType.hasPrimitives()) {
            var specializedForm = implementation.specializedImplementation;
            if (specializedForm != null) {
                // The type of specializedForm includes the leading parameters for copied values
                var cleanType = specializedForm.type().dropParameterTypes(0, copiedValues.length);
                if (cleanType.equals(requiredType)) {
                    var specializedInvoker = MethodHandles.insertArguments(specializedForm, 0, copiedValues);
                    this.specializedInvoker = specializedInvoker.asType(requiredType);
                    specializedInvokerType = requiredType;
                    return this.specializedInvoker;
                }
            }
        }
        var genericForm = implementation.genericImplementation;
        if (genericForm != null) {
            var genericInvoker = MethodHandles.insertArguments(genericForm, 0, copiedValues);
            specializedInvoker = genericInvoker.asType(requiredType);
            specializedInvokerType = requiredType;
            return specializedInvoker;
        }
        return genericInvoker.asType(requiredType);
    }

    /**
     * Return an invoker of the requested type. The type describes the call site
     * which will be bound to the invoker, so it has the additional leading
     * {@code Object} parameter to receive the closure.
     *
     * @throws IllegalArgumentException if it's impossible to create an invoker of the
     *         requested type, for example because of a function arity mismatch.
     */
    MethodHandle optimalCallSiteInvoker(MethodType callSiteType) {
        var typeWithoutLeadingClosure = callSiteType.dropParameterTypes(0, 1);
        return optimalInvoker(typeWithoutLeadingClosure);
    }

    public Object invoke() {
        try {
            return genericInvoker.invokeExact();
        } catch (Throwable throwable) {
            throw new InvocationException(throwable);
        }
    }

    public Object invoke(Object arg) {
        try {
            return genericInvoker.invokeExact(arg);
        } catch (Throwable throwable) {
            throw new InvocationException(throwable);
        }
    }

    public Object invoke(Object arg1, Object arg2) {
        try {
            return genericInvoker.invokeExact(arg1, arg2);
        } catch (Throwable throwable) {
            throw new InvocationException(throwable);
        }
    }

    public Object invoke(Object... args) {
        switch (args.length) {
            case 0:
                return invoke();
            case 1:
                return invoke(args[0]);
            case 2:
                return invoke(args[0], args[1]);
            default:
                throw new UnsupportedOperationException();
        }
    }
}
