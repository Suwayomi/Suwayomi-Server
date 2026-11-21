// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.support_lib_glue;

import android.net.Uri;
import android.webkit.WebView;
import android.util.Log;

import org.chromium.android_webview.JsReplyProxy;
import org.chromium.android_webview.WebMessageListener;
import org.chromium.content_public.browser.MessagePayload;
import org.chromium.content_public.browser.MessagePayloadType;
import org.chromium.content_public.browser.MessagePort;
import org.chromium.support_lib_boundary.WebMessageListenerBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;
import org.chromium.support_lib_boundary.util.Features;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider;

/**
 * Support library glue WebMessageListener.
 *
 * A new instance of this class is created transiently for every shared library WebViewCompat call.
 * Do not store state here.
 */
class SupportLibWebMessageListenerAdapter implements WebMessageListener {
    private static final String TAG = "WebMsgLtrAdptr";

    private final WeakReference<KcefWebViewProvider> mAwContentsRef;
    private final WebMessageListenerBoundaryInterface mImpl;
    private final String[] mSupportedFeatures;

    public SupportLibWebMessageListenerAdapter(
            KcefWebViewProvider awContents, /* WebMessageListener */ InvocationHandler handler) {
        mAwContentsRef = new WeakReference<>(awContents);
        mImpl =
                BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                        WebMessageListenerBoundaryInterface.class, handler);
        mSupportedFeatures = mImpl.getSupportedFeatures();
    }

    public /* WebMessageListener */ InvocationHandler getSupportLibInvocationHandler() {
        return Proxy.getInvocationHandler(mImpl);
    }

    @Override
    public void onPostMessage(
            final MessagePayload payload,
            final Uri topLevelOrigin,
            final Uri sourceOrigin,
            final boolean isMainFrame,
            final JsReplyProxy replyProxy,
            final MessagePort[] ports) {
        KcefWebViewProvider awContents = mAwContentsRef.get();
        if (awContents == null) return;

        if (!BoundaryInterfaceReflectionUtil.containsFeature(
                mSupportedFeatures, Features.WEB_MESSAGE_LISTENER)) {
            Log.e(TAG, "The AndroidX doesn't have feature: " + Features.WEB_MESSAGE_LISTENER);
            return;
        }

        if (supportsPayloadType(payload.getType())) {
            mImpl.onPostMessage(
                    (WebView) awContents.getPrimaryContainerView(),
                    BoundaryInterfaceReflectionUtil.createInvocationHandlerFor(
                            new SupportLibWebMessageAdapter(payload, ports)),
                    sourceOrigin,
                    isMainFrame,
                    BoundaryInterfaceReflectionUtil.createInvocationHandlerFor(
                            new SupportLibJsReplyProxyAdapter(replyProxy)));
        } else {
            Log.e(
                    TAG,
                    "The AndroidX doesn't support payload type: "
                            + MessagePayload.typeToString(payload.getType()));
        }
    }

    private boolean supportsPayloadType(@MessagePayloadType int type) {
        return switch (type) {
            case MessagePayloadType.STRING -> true;
            case MessagePayloadType.ARRAY_BUFFER ->
                BoundaryInterfaceReflectionUtil.containsFeature(
                    mSupportedFeatures, Features.WEB_MESSAGE_ARRAY_BUFFER);
            case MessagePayloadType.SHARED_ARRAY_BUFFER ->
                BoundaryInterfaceReflectionUtil.containsFeature(
                    mSupportedFeatures, Features.WEB_MESSAGE_SHARED_ARRAY_BUFFER);
            default -> false;
        };
    }
}
