/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ireader.core.http

import okio.Closeable

/**
 * A wrapper to allow executing JavaScript code without knowing the implementation details.
 * Stub implementation for JVM - JavaScript execution not supported on server
 */
class JS : Closeable {
    /**
     * Evaluates the given JavaScript [script] and returns its result as [String] or throws an
     * exception.
     */
    fun evaluateAsString(script: String): String = throw UnsupportedOperationException("JavaScript execution is not supported on server")

    /**
     * Evaluates the given JavaScript [script] and returns its result as [Int] or throws an exception.
     */
    fun evaluateAsInt(script: String): Int = throw UnsupportedOperationException("JavaScript execution is not supported on server")

    /**
     * Evaluates the given JavaScript [script] and returns its result as [Double] or throws an
     * exception.
     */
    fun evaluateAsDouble(script: String): Double = throw UnsupportedOperationException("JavaScript execution is not supported on server")

    /**
     * Evaluates the given JavaScript [script] and returns its result as [Boolean] or throws an
     * exception.
     */
    fun evaluateAsBoolean(script: String): Boolean = throw UnsupportedOperationException("JavaScript execution is not supported on server")

    /**
     * Closes this instance. No evaluations can be made on this instance after calling this method.
     */
    override fun close() {
        // Nothing to close in stub implementation
    }
}
