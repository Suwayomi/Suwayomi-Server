// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.support_lib_glue;

import android.content.Context;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.chromium.support_lib_boundary.WebViewProviderFactoryBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;

import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Support library glue version of WebViewChromiumFactoryProvider. */
public class SupportLibWebViewChromiumFactory implements WebViewProviderFactoryBoundaryInterface {
    // SupportLibWebkitToCompatConverterAdapter
    private final InvocationHandler mCompatConverterAdapter;
    private static final String[] sWebViewSupportedFeatures =
            new String[] {
                "USER_AGENT_METADATA",
            };

    public SupportLibWebViewChromiumFactory() {
        mCompatConverterAdapter =
                BoundaryInterfaceReflectionUtil.createInvocationHandlerFor(
                        new SupportLibWebkitToCompatConverterAdapter());
    }

    @Override
    public /* WebViewBuilderBoundaryInterface */ InvocationHandler getWebViewBuilder() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* WebContentBoundaryInterface */ InvocationHandler buildWebContent(
            Consumer<BiConsumer<Integer, Object>> buildConfig) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* WebViewProvider */ InvocationHandler createWebView(WebView webView) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public InvocationHandler getWebkitToCompatConverter() {
        return mCompatConverterAdapter;
    }

    @Override
    public InvocationHandler getStatics() {
        throw new RuntimeException("Not supported");
    }

    @VisibleForTesting
    public static String[] assembleSupportedFeatures() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public String[] getSupportedFeatures() {
        return sWebViewSupportedFeatures;
    }

    @Override
    public InvocationHandler getServiceWorkerController() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public InvocationHandler getTracingController() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public InvocationHandler getProxyController() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public InvocationHandler getDropDataProvider() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public InvocationHandler getProfileStore() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void startUpWebView(
            Consumer<BiConsumer<Integer, Object>> config,
            Consumer<Consumer<BiConsumer<Integer, Object>>> onSuccess,
            Consumer<Consumer<BiConsumer<Integer, Object>>> onFailure) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void startUpWebView(
            /* WebViewStartUpConfig */ InvocationHandler configInvoHandler,
            /* WebViewStartUpCallback */ InvocationHandler callbackInvoHandler) {
        throw new RuntimeException("Not supported");
    }
}
