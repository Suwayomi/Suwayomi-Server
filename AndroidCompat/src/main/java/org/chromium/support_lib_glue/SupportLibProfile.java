// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.support_lib_glue;

import android.os.CancellationSignal;
import android.os.SystemClock;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebStorage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.webview.chromium.Profile;

import org.chromium.support_lib_boundary.ProfileBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;
import org.chromium.support_lib_boundary.util.Features;

import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** The support-lib glue implementation for Profile, delegates all the calls to {@link Profile}. */
public class SupportLibProfile implements ProfileBoundaryInterface {
    private final Profile mProfileImpl;

    public SupportLibProfile(@NonNull Profile profile) {
        mProfileImpl = profile;
    }

    @NonNull
    @Override
    public String getName() {
        return mProfileImpl.getName();
    }

    @NonNull
    @Override
    public CookieManager getCookieManager() {
        return mProfileImpl.getCookieManager();
    }

    @NonNull
    @Override
    public WebStorage getWebStorage() {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    @Override
    public GeolocationPermissions getGeoLocationPermissions() {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    @Override
    public ServiceWorkerController getServiceWorkerController() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void prefetchUrl(
            String url,
            CancellationSignal cancellationSignal,
            Executor callbackExecutor,
            /* PrefetchOperationCallback */ InvocationHandler callback) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void prefetchUrl(
            String url,
            @Nullable CancellationSignal cancellationSignal,
            Executor callbackExecutor,
            /* SpeculativeLoadingParameters */ InvocationHandler speculativeLoadingParams,
            /* PrefetchOperationCallback */ InvocationHandler callback) {
        throw new RuntimeException("Stub!");
    }

    public void setCancelListener(CancellationSignal cancellationSignal, int prefetchKey) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void clearPrefetch(
            String url,
            Executor callbackExecutor,
            /* PrefetchOperationCallback */ InvocationHandler callback) {
        // Keeping this around so we don't break the Boundary Interface.
        // The method itself is deprecated.
    }

    @Override
    public void setMaxPrerenders(int maxPrerenders) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void setMaxPrerenders(@Nullable Integer maxPrerenders) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void clearMaxPrerenders() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void setMaxPrefetches(@Nullable Integer maxPrefetches) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void setPrefetchTtlSeconds(@Nullable Integer prefetchTtlSeconds) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void setMaxPrefetches(int maxPrefetches) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void setPrefetchTtlSeconds(int prefetchTtlSeconds) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void clearMaxPrefetches() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void clearPrefetchTtl() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int getMaxPrerenders() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int getMaxPrefetches() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int getPrefetchTtlSeconds() {
        throw new RuntimeException("Stub!");
    }

    /**
     * @deprecated Can be removed along with {@link
     *     org.chromium.support_lib_boundary.util.Features#SPECULATIVE_LOADING_CONFIG}
     */
    @Deprecated
    @Override
    public void setSpeculativeLoadingConfig(
            /* SpeculativeLoadingConfig */ InvocationHandler config) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void warmUpRendererProcess() {
        throw new RuntimeException("Stub!");
    }

    /**
     * @deprecated Can be removed along with {@link
     *     org.chromium.support_lib_boundary.util.Features#EXTRA_HEADER_FOR_ORIGINS}
     */
    @Deprecated
    @Override
    public void setOriginMatchedHeader(
            @NonNull String headerName,
            @NonNull String headerValue,
            @NonNull Set<String> originRules) {
        mProfileImpl.setOriginMatchedHeader(headerName, headerValue, originRules);
    }

    @Override
    public boolean hasOriginMatchedHeader(@NonNull String headerName) {
        return mProfileImpl.hasOriginMatchedHeader(headerName);
    }

    @Override
    public void addOriginMatchedHeader(
            @NonNull String name, @NonNull String value, @NonNull Set<String> rules) {
        mProfileImpl.addOriginMatchedHeader(name, value, rules);
    }

    @Override
    public @NonNull /* List<OriginMatchedBoundaryInterface> */ List<InvocationHandler>
            getOriginMatchedHeaders(@Nullable String headerName, @Nullable String headerValue) {
        throw new RuntimeException("Stub!");
    }

    /**
     * @deprecated Can be removed along with {@link
     *     org.chromium.support_lib_boundary.util.Features#EXTRA_HEADER_FOR_ORIGINS}
     */
    @Deprecated
    @Override
    public void clearOriginMatchedHeader(@NonNull String headerName) {
        clearOriginMatchedHeader(headerName, null);
    }

    @Override
    public void clearOriginMatchedHeader(@NonNull String headerName, @Nullable String headerValue) {
        mProfileImpl.clearOriginMatchedHeader(headerName, headerValue);
    }

    @Override
    public void clearAllOriginMatchedHeaders() {
        mProfileImpl.clearAllOriginMatchedHeaders();
    }

    @Override
    public Runnable addHeaderInterceptor(
            Consumer<BiConsumer<Integer, Object>> buildRequestMatcherConfig,
            Function<WebResourceRequest, Map<String, String>> headerInterceptor) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void preconnect(String url) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void enqueuePreconnect(String url) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void addQuicHints(Set<String> origins) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    @Override
    public /* HttpCacheBoundaryInterface */ InvocationHandler getHttpCache() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void setCrossOriginIsolatedAllowList(@NonNull Set<String> originPatterns) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    @Override
    public Set<String> getCrossOriginIsolatedAllowList() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void configureIsolatedSites(@NonNull Set<String> sites) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean canConfigureIsolatedSites() {
        throw new RuntimeException("Stub!");
    }
}
