// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.support_lib_glue;

import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.chromium.support_lib_boundary.WebViewProviderBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider;

/**
 * Support library glue version of WebViewChromium.
 *
 * <p>An instance of this class is created when a WebViewCompat method is called with a WebView
 * instance. WebViewCompat may hold on the new instance until the corresponding WebView is GCed.
 *
 * <p>Do not store state here.
 */
class SupportLibWebViewChromium implements WebViewProviderBoundaryInterface {
    // Use weak references to ensure that caching this object on the client side doesn’t prevent the
    // WebView from being garbage collected.
    private final WeakReference<KcefWebViewProvider> mSharedWebViewChromium;

    public SupportLibWebViewChromium(WebView webView) {
        var sharedWebViewChromium = (KcefWebViewProvider)webView.getWebViewProvider();
        mSharedWebViewChromium = new WeakReference<>(sharedWebViewChromium);
    }

    @Override
    public void insertVisualStateCallback(long requestId, InvocationHandler callbackInvoHandler) {
        // KcefWebViewProvider sharedWebViewChromium = mSharedWebViewChromium.get();
        // if (sharedWebViewChromium == null) {
        //     throw new IllegalStateException(
        //             "Support lib method called on WebView that no longer exists.");
        // }
        // sharedWebViewChromium.insertVisualStateCallback(
        //         requestId,
        //         new AwContents.VisualStateCallback() {
        //             @Override
        //             public void onComplete(long requestId) {
        //                 visualStateCallback.onComplete(requestId);
        //             }
        //         });
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* WebMessagePort */ InvocationHandler[] createWebMessageChannel() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void postMessageToMainFrame(
            /* WebMessage */ InvocationHandler message, Uri targetOrigin) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void addWebMessageListener(
            String jsObjectName,
            String[] allowedOriginRules,
            /* WebMessageListener */ InvocationHandler listener) {
        // KcefWebViewProvider sharedWebViewChromium = mSharedWebViewChromium.get();
        // if (sharedWebViewChromium == null) {
        //     throw new IllegalStateException(
        //             "Support lib method called on WebView that no longer exists.");
        // }
        // sharedWebViewChromium.addWebMessageListener(
        //         jsObjectName,
        //         allowedOriginRules,
        //         new SupportLibWebMessageListenerAdapter(
        //                 sharedWebViewChromium.getAwContents(), listener));
        throw new RuntimeException("Not supported");
    }

    @Override
    public void removeWebMessageListener(final String jsObjectName) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void addWebMessageListener(
            String jsObjectName,
            String[] allowedOriginRules,
            /* WebMessageListener */ InvocationHandler listener,
            String worldName) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void removeWebMessageListener(final String jsObjectName, String worldName) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* ScriptHandler */ InvocationHandler addJavaScriptOnEvent(
            final String script,
            final String[] allowedOriginRules,
            @JavaScriptInjectionTime int injectionEvent,
            String worldName) {
        KcefWebViewProvider sharedWebViewChromium = mSharedWebViewChromium.get();
        if (sharedWebViewChromium == null) {
            throw new IllegalStateException(
                    "Support lib method called on WebView that no longer exists.");
        }
        return BoundaryInterfaceReflectionUtil.createInvocationHandlerFor(
                SupportLibScriptHandlerAdapter.persistentJavascriptHandler(
                        sharedWebViewChromium.addJavaScriptOnEvent(
                                script,
                                getDocumentInjectionEvent(injectionEvent),
                                allowedOriginRules,
                                worldName)));
    }

    @Override
    public int getJavaScriptWorld(String worldName) {
        KcefWebViewProvider sharedWebViewChromium = mSharedWebViewChromium.get();
        if (sharedWebViewChromium == null) {
            throw new IllegalStateException(
                    "Support lib method called on WebView that no longer exists.");
        }
        return sharedWebViewChromium.getJavaScriptWorld(worldName);
    }

    private KcefWebViewProvider.ScriptType getDocumentInjectionEvent(
            @JavaScriptInjectionTime int event) {
        switch (event) {
            case JavaScriptInjectionTime.DOCUMENT_START:
                return KcefWebViewProvider.ScriptType.DOCUMENT_START;
            case JavaScriptInjectionTime.DOCUMENT_END:
                return KcefWebViewProvider.ScriptType.DOCUMENT_END;
            default:
                throw new IllegalArgumentException("Unknown event: " + event);
        }
    }

    @Override
    public /* ScriptHandler */ InvocationHandler addDocumentStartJavaScript(
            final String script, final String[] allowedOriginRules) {
        // KcefWebViewProvider sharedWebViewChromium = mSharedWebViewChromium.get();
        // if (sharedWebViewChromium == null) {
        //     throw new IllegalStateException(
        //             "Support lib method called on WebView that no longer exists.");
        // }
        // return BoundaryInterfaceReflectionUtil.createInvocationHandlerFor(
        //         SupportLibScriptHandlerAdapter.documentStartHandler(
        //                 sharedWebViewChromium.addDocumentStartJavaScript(
        //                         script, allowedOriginRules)));
        throw new RuntimeException("Not supported");
    }

    @Override
    public WebViewClient getWebViewClient() {
        KcefWebViewProvider sharedWebViewChromium = mSharedWebViewChromium.get();
        if (sharedWebViewChromium == null) {
            throw new IllegalStateException(
                    "Support lib method called on WebView that no longer exists.");
        }
        return sharedWebViewChromium.getWebViewClient();
    }

    @Override
    public WebChromeClient getWebChromeClient() {
        KcefWebViewProvider sharedWebViewChromium = mSharedWebViewChromium.get();
        if (sharedWebViewChromium == null) {
            throw new IllegalStateException(
                    "Support lib method called on WebView that no longer exists.");
        }
        return sharedWebViewChromium.getWebChromeClient();
    }

    @Override
    public /* WebViewRenderer */ InvocationHandler getWebViewRenderer() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* WebViewRendererClient */ InvocationHandler getWebViewRendererClient() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setWebViewRendererClient(
            /* WebViewRendererClient */ InvocationHandler webViewRendererClient) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setProfile(String profileName) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* Profile */ InvocationHandler getProfile() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setAudioMuted(boolean muted) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean isAudioMuted() {
        return true;
    }

    @Override
    public void prerenderUrl(
            String url,
            @Nullable CancellationSignal cancellationSignal,
            Executor callbackExecutor,
            ValueCallback<Void> activationCallback,
            ValueCallback<Throwable> errorCallback) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void prerenderUrl(
            String url,
            @Nullable CancellationSignal cancellationSignal,
            Executor callbackExecutor,
            /* SpeculativeLoadingParameters */ InvocationHandler speculativeLoadingParameters,
            ValueCallback<Void> activationCallback,
            ValueCallback<Throwable> errorCallback) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* Navigation */ InvocationHandler navigate(
            String url, /* NavigationParams */ InvocationHandler navigationParameters) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void saveState(Bundle outState, int maxSize, boolean includeForwardState) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void addWebViewNavigationListener(
            Executor executor, /* WebViewNavigationListener */ InvocationHandler listener) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void removeWebViewNavigationListener(
            /* WebViewNavigationListener */ InvocationHandler listener) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* WebViewNavigationClient */ InvocationHandler getWebViewNavigationClient() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setWebViewNavigationClient(
            /* WebViewNavigationClient */ InvocationHandler webViewNavigationClient) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public Runnable addHeaderInterceptor(
            Consumer<BiConsumer<Integer, Object>> buildRequestMatcherConfig,
            Function<WebResourceRequest, Map<String, String>> headerInterceptor) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setGoogleAccountsCallback(
            @NonNull String accountId,
            Executor executor,
            Consumer</* GoogleAccountsEvent */ InvocationHandler> consumer) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void clearGoogleAccountsCallback() {
        throw new RuntimeException("Not supported");
    }
}
