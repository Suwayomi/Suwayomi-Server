// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.support_lib_glue;

import org.chromium.android_webview.ScriptHandler;
import org.chromium.support_lib_boundary.ScriptHandlerBoundaryInterface;

/** Adapter between ScriptHandlerBoundaryInterface and ScriptHandler. */
interface SupportLibScriptHandlerAdapter extends ScriptHandlerBoundaryInterface {

    /**
     * Creates a SupportLibScriptHandlerAdapter that logs it's removing a script added by
     * ADD_DOCUMENT_START. Note: Logging and tracing API calls is the only difference between this
     * and {@code persistentJavascriptHandler}.
     */
    static SupportLibScriptHandlerAdapter documentStartHandler(ScriptHandler scriptHandler) {
        return () -> {
            scriptHandler.remove();
        };
    }

    /**
     * Creates a SupportLibScriptHandlerAdapter that logs it's removing a script added by
     * ADD_JAVA_SCRIPT_ON_EVENT. Note: Logging and tracing API calls is the only difference between
     * this and {@code documentStartHandler}.
     */
    static SupportLibScriptHandlerAdapter persistentJavascriptHandler(ScriptHandler scriptHandler) {
        return () -> {
            scriptHandler.remove();
        };
    }
}

