/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.webkit;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.Context;

import com.microsoft.playwright.Browser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// This is an base class: concrete WebViewProviders must
// create a class derived from this, and return an instance of it in the
// WebViewProvider.getWebSettingsProvider() method implementation.
public class PlaywrightWebSettings extends WebSettings {
    private Browser.NewContextOptions _options;

    public PlaywrightWebSettings() {
        _options = new Browser.NewContextOptions()
            .setJavaScriptEnabled(false);
    }

    public Browser.NewContextOptions getBrowserOptions() {
        return _options;
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    @Deprecated
    public void setNavDump(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    @Deprecated
    public boolean getNavDump() {
        throw new RuntimeException("Stub!");
    }

    public void setSupportZoom(boolean support) {
        throw new RuntimeException("Stub!");
    }

    public boolean supportZoom() {
        throw new RuntimeException("Stub!");
    }

    public void setMediaPlaybackRequiresUserGesture(boolean require) {
        throw new RuntimeException("Stub!");
    }

    public boolean getMediaPlaybackRequiresUserGesture() {
        throw new RuntimeException("Stub!");
    }

    // This method was intended to select between the built-in zoom mechanisms
    // and the separate zoom controls. The latter were obtained using
    // {@link WebView#getZoomControls}, which is now hidden.
    public void setBuiltInZoomControls(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean getBuiltInZoomControls() {
        throw new RuntimeException("Stub!");
    }

    public void setDisplayZoomControls(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean getDisplayZoomControls() {
        throw new RuntimeException("Stub!");
    }

    public void setAllowFileAccess(boolean allow) {
        throw new RuntimeException("Stub!");
    }

    public boolean getAllowFileAccess() {
        throw new RuntimeException("Stub!");
    }

    public void setAllowContentAccess(boolean allow) {
        throw new RuntimeException("Stub!");
    }

    public boolean getAllowContentAccess() {
        throw new RuntimeException("Stub!");
    }

    public void setLoadWithOverviewMode(boolean overview) {
        // cannot be enabled
    }

    public boolean getLoadWithOverviewMode() {
        return false;
    }

    @Deprecated
    public void setEnableSmoothTransition(boolean enable) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public  boolean enableSmoothTransition() {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    @Deprecated
    public  void setUseWebViewBackgroundForOverscrollBackground(boolean view) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    @Deprecated
    public  boolean getUseWebViewBackgroundForOverscrollBackground() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public  void setSaveFormData(boolean save) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public boolean getSaveFormData() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setSavePassword(boolean save) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public boolean getSavePassword() {
        throw new RuntimeException("Stub!");
    }

    public void setTextZoom(int textZoom) {
        throw new RuntimeException("Stub!");
    }

    public int getTextZoom() {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    public void setAcceptThirdPartyCookies(boolean accept) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    public boolean getAcceptThirdPartyCookies() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setDefaultZoom(ZoomDensity zoom) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public ZoomDensity getDefaultZoom() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setLightTouchEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public boolean getLightTouchEnabled() {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    @Deprecated
    public void setUserAgent(int ua) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    @Deprecated
    public int getUserAgent() {
        throw new RuntimeException("Stub!");
    }

    public void setUseWideViewPort(boolean use) {
        _options.setIsMobile(use);
    }

    public boolean getUseWideViewPort() {
        return _options.isMobile;
    }

    public void setSupportMultipleWindows(boolean support) {
        throw new RuntimeException("Stub!");
    }

    public boolean supportMultipleWindows() {
        throw new RuntimeException("Stub!");
    }

    public void setLayoutAlgorithm(LayoutAlgorithm l) {
        throw new RuntimeException("Stub!");
    }

    public LayoutAlgorithm getLayoutAlgorithm() {
        throw new RuntimeException("Stub!");
    }

    public void setStandardFontFamily(String font) {
        throw new RuntimeException("Stub!");
    }

    public String getStandardFontFamily() {
        throw new RuntimeException("Stub!");
    }

    public void setFixedFontFamily(String font) {
        throw new RuntimeException("Stub!");
    }

    public String getFixedFontFamily() {
        throw new RuntimeException("Stub!");
    }

    public void setSansSerifFontFamily(String font) {
        throw new RuntimeException("Stub!");
    }

    public String getSansSerifFontFamily() {
        throw new RuntimeException("Stub!");
    }

    public void setSerifFontFamily(String font) {
        throw new RuntimeException("Stub!");
    }

    public String getSerifFontFamily() {
        throw new RuntimeException("Stub!");
    }

    public void setCursiveFontFamily(String font) {
        throw new RuntimeException("Stub!");
    }

    public String getCursiveFontFamily() {
        throw new RuntimeException("Stub!");
    }

    public void setFantasyFontFamily(String font) {
        throw new RuntimeException("Stub!");
    }

    public String getFantasyFontFamily() {
        throw new RuntimeException("Stub!");
    }

    public void setMinimumFontSize(int size) {
        throw new RuntimeException("Stub!");
    }

    public int getMinimumFontSize() {
        throw new RuntimeException("Stub!");
    }

    public void setMinimumLogicalFontSize(int size) {
        throw new RuntimeException("Stub!");
    }

    public int getMinimumLogicalFontSize() {
        throw new RuntimeException("Stub!");
    }

    public void setDefaultFontSize(int size) {
        throw new RuntimeException("Stub!");
    }

    public int getDefaultFontSize() {
        throw new RuntimeException("Stub!");
    }

    public void setDefaultFixedFontSize(int size) {
        throw new RuntimeException("Stub!");
    }

    public int getDefaultFixedFontSize() {
        throw new RuntimeException("Stub!");
    }

    public void setLoadsImagesAutomatically(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    public boolean getLoadsImagesAutomatically() {
        throw new RuntimeException("Stub!");
    }

    public void setBlockNetworkImage(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    public boolean getBlockNetworkImage() {
        throw new RuntimeException("Stub!");
    }

    public void setBlockNetworkLoads(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    public boolean getBlockNetworkLoads() {
        throw new RuntimeException("Stub!");
    }

    public void setJavaScriptEnabled(boolean flag) {
        _options.setJavaScriptEnabled(flag);
    }

    @Deprecated
    public void setAllowUniversalAccessFromFileURLs(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setAllowFileAccessFromFileURLs(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    @Deprecated
    public void setPluginsEnabled(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setPluginState(PluginState state) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setDatabasePath(String databasePath) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setGeolocationDatabasePath(String databasePath) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setAppCacheEnabled(boolean flag) {}

    @Deprecated
    public void setAppCachePath(String appCachePath) {}

    @Deprecated
    public void setAppCacheMaxSize(long appCacheMaxSize) {}

    @Deprecated
    public void setDatabaseEnabled(boolean flag) {
        // cannot be disabled
    }

    public void setDomStorageEnabled(boolean flag) {
        // cannot be disabled
    }

    public boolean getDomStorageEnabled() {
        return true;
    }

    @Deprecated
    public String getDatabasePath() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public boolean getDatabaseEnabled() {
        return true;
    }

    public void setGeolocationEnabled(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    public boolean getJavaScriptEnabled() {
        return _options.javaScriptEnabled;
    }

    public boolean getAllowUniversalAccessFromFileURLs() {
        throw new RuntimeException("Stub!");
    }

    public boolean getAllowFileAccessFromFileURLs() {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    @Deprecated
    public boolean getPluginsEnabled() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public PluginState getPluginState() {
        throw new RuntimeException("Stub!");
    }

    public void setJavaScriptCanOpenWindowsAutomatically(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    public boolean getJavaScriptCanOpenWindowsAutomatically() {
        throw new RuntimeException("Stub!");
    }

    public void setDefaultTextEncodingName(String encoding) {
        throw new RuntimeException("Stub!");
    }

    public String getDefaultTextEncodingName() {
        throw new RuntimeException("Stub!");
    }

    public void setUserAgentString(@Nullable String ua) {
        if (ua == null) {
            _options.setUserAgent(getDefaultUserAgent(null));
        } else {
            _options.setUserAgent(ua);
        }
    }

    public String getUserAgentString() {
        return _options.userAgent;
    }

    public static String getDefaultUserAgent(Context context) {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36";
    }

    public void setNeedInitialFocus(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setRenderPriority(RenderPriority priority) {
        throw new RuntimeException("Stub!");
    }

    public void setCacheMode(int mode) {
        throw new RuntimeException("Stub!");
    }

    public int getCacheMode() {
        throw new RuntimeException("Stub!");
    }

    public void setMixedContentMode(int mode) {
        throw new RuntimeException("Stub!");
    }

    public int getMixedContentMode() {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    public void setVideoOverlayForEmbeddedEncryptedVideoEnabled(boolean flag) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @SystemApi
    public boolean getVideoOverlayForEmbeddedEncryptedVideoEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setOffscreenPreRaster(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean getOffscreenPreRaster() {
        throw new RuntimeException("Stub!");
    }

    public void setSafeBrowsingEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean getSafeBrowsingEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setDisabledActionModeMenuItems(int menuItems) {
        throw new RuntimeException("Stub!");
    }

    public int getDisabledActionModeMenuItems() {
        throw new RuntimeException("Stub!");
    }
}
