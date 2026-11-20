// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.support_lib_glue;

import android.webkit.WebSettings;

import org.chromium.support_lib_boundary.WebSettingsBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;
import xyz.nulldev.androidcompat.webkit.KcefWebSettings;

import java.lang.reflect.InvocationHandler;
import java.util.HashMap;
import java.util.Map;

/** Adapter between WebSettingsBoundaryInterface and AwSettings. */
class SupportLibWebSettingsAdapter implements WebSettingsBoundaryInterface {
    private final KcefWebSettings mAwSettings;

    /*package*/ SupportLibWebSettingsAdapter(KcefWebSettings awSettings) {
        mAwSettings = awSettings;
    }

    @Override
    public void setOffscreenPreRaster(boolean enabled) {
        mAwSettings.setOffscreenPreRaster(enabled);
    }

    @Override
    public boolean getOffscreenPreRaster() {
        return mAwSettings.getOffscreenPreRaster();
    }

    @Override
    public void setSafeBrowsingEnabled(boolean enabled) {
        mAwSettings.setSafeBrowsingEnabled(enabled);
    }

    @Override
    public boolean getSafeBrowsingEnabled() {
        return mAwSettings.getSafeBrowsingEnabled();
    }

    @Override
    public void setDisabledActionModeMenuItems(int menuItems) {
        mAwSettings.setDisabledActionModeMenuItems(menuItems);
    }

    @Override
    public int getDisabledActionModeMenuItems() {
        return mAwSettings.getDisabledActionModeMenuItems();
    }

    @Override
    public boolean getWillSuppressErrorPage() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setWillSuppressErrorPage(boolean suppressed) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setForceDark(int forceDarkMode) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public int getForceDark() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setForceDarkBehavior(int forceDarkBehavior) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public int getForceDarkBehavior() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setAlgorithmicDarkeningAllowed(boolean allow) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean isAlgorithmicDarkeningAllowed() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setWebauthnSupport(@WebauthnSupport int support) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public @WebauthnSupport int getWebauthnSupport() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setEnterpriseAuthenticationAppLinkPolicyEnabled(boolean enabled) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean getEnterpriseAuthenticationAppLinkPolicyEnabled() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setUserAgentMetadataFromMap(Map<String, Object> uaMetadata) {
        mAwSettings.setUserAgentMetadataFromMap(uaMetadata);
    }

    @Override
    public Map<String, Object> getUserAgentMetadataMap() {
        return mAwSettings.getUserAgentMetadataMap();
    }

    @Override
    @Deprecated
    public void setAttributionBehavior(@AttributionBehavior int behavior) {
        throw new UnsupportedOperationException("AttributionBehavior is not supported.");
    }

    @Override
    @Deprecated
    public int getAttributionBehavior() {
        throw new UnsupportedOperationException("AttributionBehavior is not supported.");
    }

    @Override
    public void setWebViewMediaIntegrityApiStatus(
            @WebViewMediaIntegrityApiStatus int defaultStatus,
            Map<String, @WebViewMediaIntegrityApiStatus Integer> permissionConfig) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public @WebViewMediaIntegrityApiStatus int getWebViewMediaIntegrityApiDefaultStatus() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public Map<String, @WebViewMediaIntegrityApiStatus Integer>
            getWebViewMediaIntegrityApiOverrideRules() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setSpeculativeLoadingStatus(
            @SpeculativeLoadingStatus int speculativeLoadingStatus) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public @SpeculativeLoadingStatus int getSpeculativeLoadingStatus() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setBackForwardCacheEnabled(boolean backForwardCacheEnabled) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean getBackForwardCacheEnabled() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setBackForwardCacheSettings(
            /* BackForwardCacheSettings */ InvocationHandler backForwardCacheSettings) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public /* BackForwardCacheSettings */ InvocationHandler getBackForwardCacheSettings() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setPaymentRequestEnabled(boolean enabled) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean getPaymentRequestEnabled() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setHasEnrolledInstrumentEnabled(boolean enabled) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean getHasEnrolledInstrumentEnabled() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setIncludeCookiesOnIntercept(boolean includeCookiesOnIntercept) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean getIncludeCookiesOnIntercept() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setHyperlinkContextMenuItems(@HyperlinkContextMenuItems int hyperlinkMenuItems) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setBackForwardCacheSettingsTimeout(long timeout) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setBackForwardCacheSettingsMaxPagesInCache(int pagesInCache) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setBackForwardCacheSettingsKeepForwardEntries(boolean keepForwardEntries) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public long getBackForwardCacheSettingsTimeout() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public int getBackForwardCacheSettingsMaxPagesInCache() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean getBackForwardCacheSettingsKeepForwardEntries() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void setDownloadFaviconsEnabled(boolean enabled) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean getDownloadFaviconsEnabled() {
        throw new RuntimeException("Not supported");
    }
}
