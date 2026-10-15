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

import android.support.annotation.Keep;

// Called from native code.
@SuppressWarnings("unused")
// Instruct ProGuard not to strip this type.
@Keep
public final class DuktapeException extends RuntimeException {

    // The native implementation parsed duktape stack traces here with
    // STACK_TRACE_PATTERN / STACK_TRACE_CLASS_NAME constants; this compat stub
    // never runs JS, so those fields were dropped.

    public DuktapeException(String detailMessage) {
        super(detailMessage);
    }
}