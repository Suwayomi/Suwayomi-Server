// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import java.lang.ref.WeakReference;

import androidx.annotation.Nullable;

import org.chromium.content_public.browser.MessagePayload;
import org.chromium.content_public.browser.MessagePayloadType;

/**
 * Used for Js Java interaction, to receive postMessage back to the injected JavaScript object. When
 * the native counterpart of this object is gone, we still don't know if this is ready for gc since
 * developer could hold a reference to it. So just cut the connection between native and Java.
 */
public class JsReplyProxy extends AwSupportLibIsomorphic {
    public static interface NativeJsReplyProxy {
        void postMessage(MessagePayload payload);
        void executeJavaScript(String script, JavaScriptExecutionCallback callback);
        void release();
    }

    private @Nullable WeakReference<NativeJsReplyProxy> mNativeJsReplyProxy;

    private JsReplyProxy(NativeJsReplyProxy nativeJsReplyProxy) {
        mNativeJsReplyProxy = new WeakReference<>(nativeJsReplyProxy);
    }

    /**
     * Post message to the injected JavaScript object. Note that it will drop message if the
     * injected object is gone.
     *
     * @throws IllegalStateException if the payload is a {@link
     *     MessagePayloadType#SHARED_ARRAY_BUFFER} and the target frame, which is otherwise able to
     *     receive the message, is not cross-origin isolated.
     */
    public void postMessage(final MessagePayload payload) {
        if (mNativeJsReplyProxy == null) return;
        NativeJsReplyProxy proxy = mNativeJsReplyProxy.get();
        if (proxy == null) return;
        proxy.postMessage(payload);
    }

    /**
     * Executes JavaScript in the same frame and world as the injected JavaScript object and returns
     * the result. The callback's onError will be called if the frame is gone or if the underlying
     * messagelistener that supported this proxy is removed.
     */
    public void executeJavaScript(
            final String script, @Nullable final JavaScriptExecutionCallback callback) {
        NativeJsReplyProxy proxy = mNativeJsReplyProxy != null ? mNativeJsReplyProxy.get() : null;
        if (proxy == null) {
            if (callback != null) {
                // If the proxy was destroyed, it's probably because the handler was
                // removed.
                callback.onError(/* JavaScriptExecutionError.FRAME_DESTROYED */ 1);
            }
            return;
        }
        proxy.executeJavaScript(script, callback);
    }

    public static JsReplyProxy create(NativeJsReplyProxy nativeJsReplyProxy) {
        return new JsReplyProxy(nativeJsReplyProxy);
    }

    private void onDestroy() {
        if (mNativeJsReplyProxy != null) {
            NativeJsReplyProxy proxy = mNativeJsReplyProxy.get();
            if (proxy != null)
                proxy.release();
            mNativeJsReplyProxy.clear();
            mNativeJsReplyProxy = null;
        }
    }

    private static void onEvaluateJavaScriptResult(
            String result,
            boolean success,
            int errorCode,
            JavaScriptExecutionCallback callback) {
        if (success) {
            callback.onSuccess(result);
        } else {
            callback.onError(errorCode);
        }
    }
}
