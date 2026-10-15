package com.squareup.duktape;

/* part of tachiyomi-extensions which is licensed under Apache License Version 2.0 */

import java.io.Closeable;
import java.io.IOException;

/** This is the reference Duktape stub that tachiyomi's extensions depend on.
 * Intended to be used as a reference; its signatures mirror {@link Duktape}
 * (including {@code get} and the two-arg {@code evaluate}) so extension code
 * compiles against the same surface, and like {@link Duktape} every entry
 * point throws {@link UnsupportedOperationException}.
 */
public class DuktapeStub implements Closeable {

    private static final String NOT_SUPPORTED =
            "Duktape is not supported in Suwayomi AndroidCompat, use app.cash.quickjs.QuickJs";

    public static Duktape create() {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public synchronized void close() throws IOException {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    public synchronized Object evaluate(String script) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    public synchronized Object evaluate(String script, String fileName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    public synchronized <T> void set(String name, Class<T> type, T object) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    public synchronized <T> T get(String name, Class<T> type) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }
}
