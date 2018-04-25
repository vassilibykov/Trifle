// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

/**
 * A mutable call site with inline cache management support. A site is
 * initialized with a dispatch method and optionally, a separate megamorphic
 * dispatch method. The dispatch method is invoked to dispatch a call at
 * the call site. It is expected that the method will use {@link #addCacheEntry}
 * to add an inline cache entry to bypass the original dispatch for a future
 * call, if the call meets the cache guard condition.
 *
 * <p>An instance counts the number of cache entries already established. If
 * their number exceeds {@link #CACHE_LIMIT}, the call site becomes megamorphic.
 * If a {@link #megamorphicDispatch} method handle has been specified at the
 * call site creation time, it becomes the dispatch method. Otherwise, the call
 * site reverts to the original dispatch method. However, because the site is
 * now in the megamorphic state, further attempts to add inline cache entries
 * will be ignored.
 */
class InlineCachingCallSite extends MutableCallSite {
    static final int CACHE_LIMIT = 3;

    /** The original dispatch method installed in this call site. */
    private final MethodHandle dispatch;
    /** The dispatch method to permanently switch to when the cache limit is hit. */
    @Nullable private final MethodHandle megamorphicDispatch;
    private int cacheSize = 0;

    InlineCachingCallSite(MethodType type, MethodHandle dispatch, @Nullable MethodHandle megamorphicDispatch) {
        super(type);
        this.dispatch = dispatch.bindTo(this).asType(type);
        this.megamorphicDispatch = megamorphicDispatch != null
            ? megamorphicDispatch.bindTo(this).asType(type)
            : null;
        setTarget(this.dispatch);
    }

    InlineCachingCallSite(MethodType type, MethodHandle dispatch) {
        this(type, dispatch, null);
    }

    /**
     * Indicate whether this site is in the megamorphic state. There is a
     * potential check-then-act issue when this method is used by a dispatch
     * method to decide whether to build and add an inline cache entry. However,
     * it is harmless and will only result in an one ignored attempt to do so.
     */
    synchronized boolean isMegamorphic() {
        return cacheSize > CACHE_LIMIT;
    }

    /**
     * Invoked by the dispatch method to establish an inline cache entry. If the
     * site is megamorphic, this method does nothing.
     */
    synchronized void addCacheEntry(MethodHandle guard, MethodHandle guardedPath) {
        if (cacheSize < CACHE_LIMIT) {
            cacheSize++;
            MethodHandle entry = MethodHandles.guardWithTest(guard, guardedPath, getTarget());
            setTarget(entry);
        } else if (cacheSize == CACHE_LIMIT) {
            cacheSize++;
            setTarget(megamorphicDispatch != null ? megamorphicDispatch : dispatch);
        }
    }

    synchronized void reset() {
        cacheSize = 0;
        setTarget(dispatch);
    }
}
