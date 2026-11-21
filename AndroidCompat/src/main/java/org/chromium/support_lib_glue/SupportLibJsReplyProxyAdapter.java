// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.support_lib_glue;

import org.chromium.android_webview.JavaScriptExecutionCallback;
import org.chromium.android_webview.JsReplyProxy;
import org.chromium.content_public.browser.MessagePayload;
import org.chromium.support_lib_boundary.ExecuteJavaScriptCallbackBoundaryInterface;
import org.chromium.support_lib_boundary.JsReplyProxyBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;

import java.lang.reflect.InvocationHandler;
import java.util.concurrent.Callable;

/** Adapter between JsReplyProxyBoundaryInterface and JsReplyProxy. */
class SupportLibJsReplyProxyAdapter implements JsReplyProxyBoundaryInterface {
    private final JsReplyProxy mReplyProxy;

    public SupportLibJsReplyProxyAdapter(JsReplyProxy replyProxy) {
        mReplyProxy = replyProxy;
    }

    @Override
    public void postMessage(String message) {
        mReplyProxy.postMessage(new MessagePayload(message));
    }

    @Override
    public void postMessageWithPayload(/* MessagePayload */ InvocationHandler payload) {
        mReplyProxy.postMessage(SupportLibWebMessagePayloadAdapter.toMessagePayload(payload));
    }

    @Override
    public void executeJavaScript(
            String javaScript, /* ExecuteJavaScriptCallbackBoundaryInterface */
            InvocationHandler callback) {
        mReplyProxy.executeJavaScript(javaScript, createExecutionCallback(callback));
    }

    @Override
    public Object getOrCreatePeer(Callable<Object> creationCallable) {
        return mReplyProxy.getOrCreateSupportLibObject(creationCallable);
    }

    private JavaScriptExecutionCallback createExecutionCallback(
            /* ExecuteJavaScriptCallbackBoundaryInterface */ InvocationHandler callback) {
        ExecuteJavaScriptCallbackBoundaryInterface executionCallback =
                BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                        ExecuteJavaScriptCallbackBoundaryInterface.class, callback);
        return new JavaScriptExecutionCallback() {
            @Override
            public void onSuccess(String result) {
                executionCallback.onSuccess(result);
            }

            @Override
            public void onError(int errorCode) {
                mapFailure(executionCallback, errorCode);
            }
        };
    }

    private void mapFailure(
            ExecuteJavaScriptCallbackBoundaryInterface callback,
            int errorCode) {
        int type = errorCode == 1 ? 1 : 0;
        String message = errorCode != 1 ? "Unknown error occurred." : null;
        callback.onFailure(type, message);
    }
}
