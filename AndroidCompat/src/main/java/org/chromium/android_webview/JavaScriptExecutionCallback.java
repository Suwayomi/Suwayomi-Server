// Copyright 2026 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

public interface JavaScriptExecutionCallback {
    default void onSuccess(String result) {}

    default void onError(int errorCode) {}
}
