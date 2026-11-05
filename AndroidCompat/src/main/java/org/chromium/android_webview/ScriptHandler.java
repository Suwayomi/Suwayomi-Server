// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import java.lang.ref.WeakReference;

import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider;

/** Used for Js Java interaction, to delete the document start JavaScript snippet. */
public class ScriptHandler {
    private final WeakReference<KcefWebViewProvider> mAwContentsRef;
    private final int mScriptId;

    public ScriptHandler(KcefWebViewProvider awContents, int scriptId) {
        assert scriptId >= 0;
        mAwContentsRef = new WeakReference<>(awContents);
        mScriptId = scriptId;
    }

    // Must be called on UI thread.
    public void remove() {
        KcefWebViewProvider awContents = mAwContentsRef.get();
        if (awContents == null) return;
        awContents.removeDocumentStartJavaScript(mScriptId);
    }
}
