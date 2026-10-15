/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.squareup.duktape;

import java.io.Closeable;

/* Note:
 * The old implementation for duktape-android used the Nashorn engine which is deprecated,
 * and a later revision looked up Mozilla Rhino via JSR-223. Neither engine is on the
 * Suwayomi AndroidCompat classpath, so that lookup always returned null and every call
 * ended in a NullPointerException or NotImplementedError.
 *
 * Callers that need a JavaScript engine should use {@link app.cash.quickjs.QuickJs},
 * which is backed by the GraalVM polyglot implementation that ships with this module.
 */

/**
 * A simple EMCAScript (Javascript) interpreter.
 *
 * <p><b>Unsupported:</b> this class is only kept for binary/source compatibility with
 * extensions compiled against duktape-android. Every entry point throws
 * {@link UnsupportedOperationException}; use {@code app.cash.quickjs.QuickJs} instead.
 */
public final class Duktape implements Closeable, AutoCloseable {

    private static final String NOT_SUPPORTED =
            "Duktape is not supported in Suwayomi AndroidCompat, use app.cash.quickjs.QuickJs";

    /**
     * Create a new interpreter instance.
     *
     * @throws UnsupportedOperationException always; use {@code app.cash.quickjs.QuickJs}.
     */
    public static Duktape create() {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    private Duktape() {}

    /**
     * Evaluate {@code script} and return a result.
     *
     * @throws UnsupportedOperationException always; use {@code app.cash.quickjs.QuickJs}.
     */
    public synchronized Object evaluate(String script) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    /**
     * Provides {@code object} to JavaScript as a global object called {@code name}.
     *
     * @throws UnsupportedOperationException always; use {@code app.cash.quickjs.QuickJs}.
     */
    public synchronized <T> void set(String name, Class<T> type, T object) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    /**
     * Attaches to a global JavaScript object called {@code name} that implements {@code type}.
     *
     * @throws UnsupportedOperationException always; use {@code app.cash.quickjs.QuickJs}.
     */
    public synchronized <T> T get(final String name, final Class<T> type) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public synchronized void close() {}
}
