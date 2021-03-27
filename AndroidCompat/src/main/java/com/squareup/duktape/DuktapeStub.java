package com.squareup.duktape;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
// part of tachiyomi-extensions which was originally licensed under Apache License Version 2.0


import java.io.Closeable;
import java.io.IOException;

/** This is the reference Duktape stub that tachiyomi's extensions depend on.
 * Intended to be used as a reference.
 */
public class DuktapeStub implements Closeable {

    public static Duktape create() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public synchronized void close() throws IOException {
        throw new RuntimeException("Stub!");
    }

    public synchronized Object evaluate(String script) {
        throw new RuntimeException("Stub!");
    }

    public synchronized <T> void set(String name, Class<T> type, T object) {
        throw new RuntimeException("Stub!");
    }

}