// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.support_lib_boundary;

import android.webkit.WebView;

import androidx.annotation.IntDef;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Boundary interface for WebView globals and singletons. */
public interface WebViewProviderFactoryBoundaryInterface {

    // LINT.IfChange(MultiCookieKeys)
    String MULTI_COOKIE_HEADER_NAME = "\0Set-Cookie-Multivalue\0";
    String MULTI_COOKIE_VALUE_SEPARATOR = "\0";

    // LINT.ThenChange(/components/embedder_support/android/util/web_resource_response.cc:MultiCookieKeys)

    /* WebViewBuilderBoundaryInterface */ InvocationHandler getWebViewBuilder();

    /* WebContentBoundaryInterface */ InvocationHandler buildWebContent(
            /* Config= */ Consumer<BiConsumer<Integer, Object>> buildConfig);

    /* SupportLibraryWebViewChromium */ InvocationHandler createWebView(WebView webview);

    /* SupportLibWebkitToCompatConverter */ InvocationHandler getWebkitToCompatConverter();

    /* StaticsAdapter */ InvocationHandler getStatics();

    String[] getSupportedFeatures();

    /* SupportLibraryServiceWorkerController */ InvocationHandler getServiceWorkerController();

    /* SupportLibraryTracingController */ InvocationHandler getTracingController();

    /* SupportLibraryProxyController */ InvocationHandler getProxyController();

    /* DropDataContentProviderBoundaryInterface*/ InvocationHandler getDropDataProvider();

    /* ProfileStoreBoundaryInterface */ InvocationHandler getProfileStore();

    /**
     * Initial version of the API, covered by {@link
     * org.chromium.support_lib_boundary.util.Features.ASYNC_WEBVIEW_STARTUP_V2}
     */
    void startUpWebView(
            Consumer<BiConsumer<Integer, Object>> config,
            Consumer<Consumer<BiConsumer<Integer, Object>>> onSuccess,
            Consumer<Consumer<BiConsumer<Integer, Object>>> onFailure);

    /**
     * Initial version of the API, covered by {@link
     * org.chromium.support_lib_boundary.util.Features.ASYNC_WEBVIEW_STARTUP}
     */
    void startUpWebView(
            /* WebViewStartUpConfig */ InvocationHandler config,
            /* WebViewStartUpCallback */ InvocationHandler callback);
}

