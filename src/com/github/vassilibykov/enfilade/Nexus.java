package com.github.vassilibykov.enfilade;

import org.jetbrains.annotations.TestOnly;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;

/**
 * A nexus of method representation and execution. Keeps track of existing
 * compiled form(s) of the method and able to execute the method in
 * either interpreted or compiled mode.
 */
class Nexus {
    /** The number of times a method is executed interpreted before it's queued for compilation. */
    private static final int PROFILING_THRESHOLD = 100;

    /*
        Instance
     */

    private final Method method;
    private MethodHandle compiledForm;
    private SwitchPoint invalidator;

    Nexus(Method method) {
        this.method = method;
        this.invalidator = new SwitchPoint();
    }

    // TODO this and others shouldn't be wholesale synchronized; not around the interpret part anyway
    public synchronized Object invoke() {
        if (compiledForm != null) {
            try {
                return compiledForm.invoke();
            } catch (Throwable e) {
                throw new RuntimeException("error invoking method compiled form", e); // TODO should do something better
            }
        } else {
            Object result = Interpreter.INSTANCE.interpret(method);
            if (method.profile.invocationCount() > PROFILING_THRESHOLD) {
                scheduleCompilation();
            }
            return result;
        }
    }

    public synchronized Object invoke(Object arg) {
        if (compiledForm != null) {
            try {
                return compiledForm.invoke(arg);
            } catch (Throwable e) {
                throw new RuntimeException("error invoking method compiled form", e); // TODO should do something better
            }
        } else {
            Object result = Interpreter.INSTANCE.interpret(method, arg);
            if (method.profile.invocationCount() > PROFILING_THRESHOLD) {
                scheduleCompilation();
            }
            return result;
        }
    }

    public synchronized Object invoke(Object arg1, Object arg2) {
        if (compiledForm != null) {
            try {
                return compiledForm.invoke(arg1, arg2);
            } catch (Throwable e) {
                throw new RuntimeException("error invoking method compiled form", e); // TODO should do something better
            }
        } else {
            Object result = Interpreter.INSTANCE.interpret(method, arg1, arg2);
            if (method.profile.invocationCount() > PROFILING_THRESHOLD) {
                scheduleCompilation();
            }
            return result;
        }
    }

    /*internal*/ synchronized void setCompiledForm(MethodHandle newCompiledForm) {
        boolean replacingOld = this.compiledForm != null;
        this.compiledForm = newCompiledForm;
        if (replacingOld) {
            SwitchPoint.invalidateAll(new SwitchPoint[]{invalidator}); // TODO this is likely to be batched in future
            this.invalidator = new SwitchPoint();
        }
    }

    /*
        For now let's just keep all the machinery here.
     */

    private static GeneratedClassLoader classLoader = new GeneratedClassLoader(Nexus.class.getClassLoader());

    private void scheduleCompilation() {
        // For now no scheduling, just compile and set synchronously.
        forceCompile();
    }

    @TestOnly
    void forceCompile() {
        if (compiledForm == null) {
            Compiler.Result result = Compiler.compile(method);
            classLoader.add(result);
            try {
                Class<?> implClass = classLoader.loadClass(result.className());
                MethodHandle compiledMethod = MethodHandles.lookup()
                    .findStatic(implClass, Compiler.IMPL_METHOD_NAME, MethodType.genericMethodType(method.arity()));
                setCompiledForm(compiledMethod);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }
}
