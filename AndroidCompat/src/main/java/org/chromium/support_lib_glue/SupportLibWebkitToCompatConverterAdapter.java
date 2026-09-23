// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.support_lib_glue;

import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.SafeBrowsingResponse;
import android.webkit.ServiceWorkerWebSettings;
import android.webkit.WebMessagePort;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;

import org.chromium.support_lib_boundary.WebkitToCompatConverterBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;
import xyz.nulldev.androidcompat.webkit.KcefWebSettings;

import java.lang.reflect.InvocationHandler;

/**
 * Adapter used for fetching implementations for Compat objects given their corresponding
 * webkit-object.
 */
class SupportLibWebkitToCompatConverterAdapter implements WebkitToCompatConverterBoundaryInterface {
    SupportLibWebkitToCompatConverterAdapter() {}

    // WebSettingsBoundaryInterface
    @Override
    public InvocationHandler convertSettings(WebSettings webSettings) {
        if (!(webSettings instanceof KcefWebSettings)) throw new ClassCastException("Expected a KcefWebSettings. Hand us a type we constructed.");
        return BoundaryInterfaceReflectionUtil.createInvocationHandlerFor(
                new SupportLibWebSettingsAdapter((KcefWebSettings)webSettings));
    }

    // WebResourceRequestBoundaryInterface
    @Override
    public InvocationHandler convertWebResourceRequest(WebResourceRequest request) {
        throw new RuntimeException("Not supported");
    }

    // ServiceWorkerWebSettingsBoundaryInterface
    @Override
    public InvocationHandler convertServiceWorkerSettings(
            /* ServiceWorkerWebSettings */ Object serviceWorkerWebSettings) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* ServiceWorkerWebSettings */ Object convertServiceWorkerSettings(
            /* SupportLibServiceWorkerSettings */ InvocationHandler serviceWorkerSettings) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* SupportLibWebResourceError */ InvocationHandler convertWebResourceError(
            /* WebResourceError */ Object webResourceError) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* WebResourceError */ Object convertWebResourceError(
            /* SupportLibWebResourceError */ InvocationHandler webResourceError) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* SupportLibSafeBrowsingResponse */ InvocationHandler convertSafeBrowsingResponse(
            /* SafeBrowsingResponse */ Object safeBrowsingResponse) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* SafeBrowsingResponse */ Object convertSafeBrowsingResponse(
            /* SupportLibSafeBrowsingResponse */ InvocationHandler safeBrowsingResponse) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* SupportLibWebMessagePort */ InvocationHandler convertWebMessagePort(
            /* WebMessagePort */ Object webMessagePort) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* WebMessagePort */ Object convertWebMessagePort(
            /* SupportLibWebMessagePort */ InvocationHandler webMessagePort) {
        throw new RuntimeException("Not supported");
    }

    // WebViewCookieManagerBoundaryInterface
    @Override
    public InvocationHandler convertCookieManager(Object cookieManager) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* WebStorageAdapter */ InvocationHandler convertWebStorage(Object webStorage) {
        throw new RuntimeException("Not supported");
    }
}
