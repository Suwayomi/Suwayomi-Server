package com.squareup.duktape;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlin.NotImplementedError;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.Closeable;

/* Note (March 2021):
 * The old implementation for duktape-android used the nashorn engine which is deprecated.
 * This new implementation uses Mozilla's Rhino: https://github.com/mozilla/rhino
 */

/**
 * A simple EMCAScript (Javascript) interpreter.
 */
public final class Duktape implements Closeable, AutoCloseable {

    private ScriptEngineManager factory = new ScriptEngineManager();
    private ScriptEngine engine = factory.getEngineByName("rhino");

    /**
   * Create a new interpreter instance. Calls to this method <strong>must</strong> matched with
   * calls to {@link #close()} on the returned instance to avoid leaking native memory.
   */
    public static Duktape create() {
        return new Duktape();
    }

    private Duktape() {}

    /**
   * Evaluate {@code script} and return a result. Note that the result must be one of the
   * supported Java types or the call will return null.
   *
   * @throws DuktapeException if there is an error evaluating the script.
   */
    public synchronized Object evaluate(String script) {
        try {
            return engine.eval(script);
        } catch (ScriptException e) {
            throw new DuktapeException(e.getMessage());
        }
    }

    /**
   * Provides {@code object} to JavaScript as a global object called {@code name}. {@code type}
   * defines the interface implemented by {@code object} that will be accessible to JavaScript.
   * {@code type} must be an interface that does not extend any other interfaces, and cannot define
   * any overloaded methods.
   * <p>Methods of the interface may return {@code void} or any of the following supported argument
   * types: {@code boolean}, {@link Boolean}, {@code int}, {@link Integer}, {@code double},
   * {@link Double}, {@link String}.
   */
    public synchronized <T> void set(String name, Class<T> type, T object) {
        throw new NotImplementedError("Not implemented!");
    }

//    /**
//   * Attaches to a global JavaScript object called {@code name} that implements {@code type}.
//   * {@code type} defines the interface implemented in JavaScript that will be accessible to Java.
//   * {@code type} must be an interface that does not extend any other interfaces, and cannot define
//   * any overloaded methods.
//   * <p>Methods of the interface may return {@code void} or any of the following supported argument
//   * types: {@code boolean}, {@link Boolean}, {@code int}, {@link Integer}, {@code double},
//   * {@link Double}, {@link String}.
//   */
//    public synchronized <T> T get(final String name, final Class<T> type) {
//        throw new NotImplementedError("Not implemented!");
//    }

    /**
   * Release the native resources associated with this object. You <strong>must</strong> call this
   * method for each instance to avoid leaking native memory.
   */
    @Override
    public synchronized void close() {}
}