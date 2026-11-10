// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.android.webview.chromium;

import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ServiceWorkerController;
import android.webkit.WebStorage;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider;

/**
 * An abstraction of {@link AwBrowserContext}, this class reflects the state needed for the
 * multi-profile public API.
 *
 * <p><b>Lifecycle Note:</b> The internal state of this class is {@code null} upon construction. It
 * remains {@code null} until a method requiring Chromium state is called on this instance, at which
 * point Chromium startup is triggered and the state is populated by {@link #initializeProfile()}.
 *
 * <p>Note that this initial population may block the calling thread while synchronizing with the UI
 * thread.
 */
public class Profile {
    private static final String TAG = "Profile";

    private static class State {
        @NonNull public final KcefWebViewProvider browserContext;
        @NonNull public final CookieManager cookieManager;
        @NonNull public final WebStorage webStorage;
        @NonNull public final GeolocationPermissions geolocationPermissions;
        @NonNull public final ServiceWorkerController serviceWorkerController;

        public State(
                @NonNull KcefWebViewProvider browserContext,
                @NonNull CookieManager cookieManager,
                @NonNull WebStorage webStorage,
                @NonNull GeolocationPermissions geolocationPermissions,
                @NonNull ServiceWorkerController serviceWorkerController) {
            this.browserContext = browserContext;
            this.cookieManager = cookieManager;
            this.webStorage = webStorage;
            this.geolocationPermissions = geolocationPermissions;
            this.serviceWorkerController = serviceWorkerController;
        }
    }

    @NonNull private final String mName;
    @NonNull private final String mTraceArgs;

    private State mState;

    public Profile(@NonNull final String profileName, @NonNull KcefWebViewProvider awInit) {
        mName = profileName;
        mTraceArgs = String.format("{name: \"%s\"}", mName);
        mState = new State(awInit, null, null, null, null);
    }

    @NonNull
    private State getInitializedState() {
        assert mState != null;
        return mState;
    }

    public KcefWebViewProvider getBrowserContext() {
        return getInitializedState().browserContext;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @NonNull
    public CookieManager getCookieManager() {
        State state = getInitializedState();
        return state.cookieManager;
    }

    @NonNull
    public WebStorage getWebStorage() {
        return getInitializedState().webStorage;
    }

    @NonNull
    public GeolocationPermissions getGeolocationPermissions() {
        return getInitializedState().geolocationPermissions;
    }

    @NonNull
    public ServiceWorkerController getServiceWorkerController() {
        return getInitializedState().serviceWorkerController;
    }

    public void setOriginMatchedHeader(
            String headerName, String headerValue, Set<String> originRules) {
        getInitializedState()
                .browserContext
                .setOriginMatchedHeader(headerName, headerValue, originRules);
    }

    public void addOriginMatchedHeader(
            String headerName, String headerValue, Set<String> originRules) {
        getInitializedState()
                .browserContext
                .addOriginMatchedHeader(headerName, headerValue, originRules);
    }

    public boolean hasOriginMatchedHeader(String headerName) {
        return getInitializedState()
                .browserContext
                .hasOriginMatchedHeader(headerName);
    }

    public void clearOriginMatchedHeader(String headerName, @Nullable String headerValue) {
        getInitializedState()
                .browserContext
                .clearOriginMatchedHeader(headerName, headerValue);
    }

    public void clearAllOriginMatchedHeaders() {
        getInitializedState()
                .browserContext
                .clearAllOriginMatchedHeaders();
    }
}
