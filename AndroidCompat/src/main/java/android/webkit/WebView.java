/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.Widget;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.StrictMode;
import android.print.PrintDocumentAdapter;
import android.util.AttributeSet;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewStructure;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textclassifier.TextClassifier;
import android.widget.AbsoluteLayout;

import java.io.BufferedWriter;
import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

// Implementation notes.
// The WebView is a thin API class that delegates its public API to a backend WebViewProvider
// class instance. WebView extends {@link AbsoluteLayout} for backward compatibility reasons.
// Methods are delegated to the provider implementation: all public API methods introduced in this
// file are fully delegated, whereas public and protected methods from the View base classes are
// only delegated where a specific need exists for them to do so.
@Widget
public class WebView extends AbsoluteLayout
        implements ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewGroup.OnHierarchyChangeListener /*, ViewDebug.HierarchyHandler */ {

    private static final String LOGTAG = "WebView";

    // Throwing an exception for incorrect thread usage if the
    // build target is JB MR2 or newer. Defaults to false, and is
    // set in the WebView constructor.
    private static volatile boolean sEnforceThreadChecking = false;

    public class WebViewTransport {
        private WebView mWebview;

        public synchronized void setWebView(@Nullable WebView webview) {
            mWebview = webview;
        }

        @Nullable
        public synchronized WebView getWebView() {
            return mWebview;
        }
    }

    public static final String SCHEME_GEO = "geo:0,0?q=";

    public interface FindListener {
        public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
            boolean isDoneCounting);
    }

    public static abstract class VisualStateCallback {
        public abstract void onComplete(long requestId);
    }

    @Deprecated
    public interface PictureListener {
        @Deprecated
        void onNewPicture(WebView view, @Nullable Picture picture);
    }

    public static class HitTestResult {
        public static final int EDIT_TEXT_TYPE = 9;

        private int mType;
        private String mExtra;

        @SystemApi
        public HitTestResult() {
        }

        @SystemApi
        public void setType(int type) {
            mType = type;
        }

        @SystemApi
        public void setExtra(String extra) {
            mExtra = extra;
        }

        public int getType() {
            return mType;
        }

        @Nullable
        public String getExtra() {
            return mExtra;
        }
    }

    public WebView(@NonNull Context context) {
        this(context, null);
    }

    public WebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public WebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr, defStyleRes, null, false);
    }

    @Deprecated
    public WebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            boolean privateBrowsing) {
        this(context, attrs, defStyleAttr, 0, null, privateBrowsing);
    }

    protected WebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            @Nullable Map<String, Object> javaScriptInterfaces, boolean privateBrowsing) {
        this(context, attrs, defStyleAttr, 0, javaScriptInterfaces, privateBrowsing);
    }

    @SuppressWarnings("deprecation")  // for super() call into deprecated base class constructor.
    protected WebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes, @Nullable Map<String, Object> javaScriptInterfaces,
            boolean privateBrowsing) {
        super(context, attrs, defStyleAttr, defStyleRes);

        if (context == null) {
            throw new IllegalArgumentException("Invalid context argument");
        }
        if (mWebViewThread == null) {
            throw new RuntimeException(
                "WebView cannot be initialized on a thread that has no Looper.");
        }
        sEnforceThreadChecking = false;
        checkThread();

        ensureProviderCreated();
        mProvider.init(javaScriptInterfaces, privateBrowsing);
    }

    @Deprecated
    public void setHorizontalScrollbarOverlay(boolean overlay) {
    }

    @Deprecated
    public void setVerticalScrollbarOverlay(boolean overlay) {
    }

    @Deprecated
    public boolean overlayHorizontalScrollbar() {
        // The old implementation defaulted to true, so return true for consistency
        return true;
    }

    @Deprecated
    public boolean overlayVerticalScrollbar() {
        // The old implementation defaulted to false, so return false for consistency
        return false;
    }

    @Deprecated
    public int getVisibleTitleHeight() {
        checkThread();
        return mProvider.getVisibleTitleHeight();
    }

    @Nullable
    public SslCertificate getCertificate() {
        checkThread();
        return mProvider.getCertificate();
    }

    @Deprecated
    public void setCertificate(SslCertificate certificate) {
        checkThread();
        mProvider.setCertificate(certificate);
    }

    //-------------------------------------------------------------------------
    // Methods called by activity
    //-------------------------------------------------------------------------

    @Deprecated
    public void savePassword(String host, String username, String password) {
        checkThread();
        mProvider.savePassword(host, username, password);
    }

    @Deprecated
    public void setHttpAuthUsernamePassword(String host, String realm,
            String username, String password) {
        checkThread();
        mProvider.setHttpAuthUsernamePassword(host, realm, username, password);
    }

    @Deprecated
    @Nullable
    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        checkThread();
        return mProvider.getHttpAuthUsernamePassword(host, realm);
    }

    public void destroy() {
        checkThread();
        mProvider.destroy();
    }

    @Deprecated
    public static void enablePlatformNotifications() {
        // noop
    }

    @Deprecated
    public static void disablePlatformNotifications() {
        // noop
    }

    public static void freeMemoryForTests() {
        throw new RuntimeException("Stub!");
    }

    public void setNetworkAvailable(boolean networkUp) {
        checkThread();
        mProvider.setNetworkAvailable(networkUp);
    }

    @Nullable
    public WebBackForwardList saveState(@NonNull Bundle outState) {
        checkThread();
        return mProvider.saveState(outState);
    }

    @Deprecated
    public boolean savePicture(Bundle b, final File dest) {
        checkThread();
        return mProvider.savePicture(b, dest);
    }

    @Deprecated
    public boolean restorePicture(Bundle b, File src) {
        checkThread();
        return mProvider.restorePicture(b, src);
    }

    @Nullable
    public WebBackForwardList restoreState(@NonNull Bundle inState) {
        checkThread();
        return mProvider.restoreState(inState);
    }

    public void loadUrl(@NonNull String url, @NonNull Map<String, String> additionalHttpHeaders) {
        checkThread();
        mProvider.loadUrl(url, additionalHttpHeaders);
    }

    public void loadUrl(@NonNull String url) {
        checkThread();
        mProvider.loadUrl(url);
    }

    public void postUrl(@NonNull String url, @NonNull byte[] postData) {
        checkThread();
        if (URLUtil.isNetworkUrl(url)) {
            mProvider.postUrl(url, postData);
        } else {
            mProvider.loadUrl(url);
        }
    }

    public void loadData(@NonNull String data, @Nullable String mimeType,
            @Nullable String encoding) {
        checkThread();
        mProvider.loadData(data, mimeType, encoding);
    }

    public void loadDataWithBaseURL(@Nullable String baseUrl, @NonNull String data,
            @Nullable String mimeType, @Nullable String encoding, @Nullable String historyUrl) {
        checkThread();
        mProvider.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
    }

    public void evaluateJavascript(@NonNull String script, @Nullable ValueCallback<String>
            resultCallback) {
        checkThread();
        mProvider.evaluateJavaScript(script, resultCallback);
    }

    public void saveWebArchive(@NonNull String filename) {
        checkThread();
        mProvider.saveWebArchive(filename);
    }

    public void saveWebArchive(@NonNull String basename, boolean autoname,
            @Nullable ValueCallback<String> callback) {
        checkThread();
        mProvider.saveWebArchive(basename, autoname, callback);
    }

    public void stopLoading() {
        checkThread();
        mProvider.stopLoading();
    }

    public void reload() {
        checkThread();
        mProvider.reload();
    }

    public boolean canGoBack() {
        checkThread();
        return mProvider.canGoBack();
    }

    public void goBack() {
        checkThread();
        mProvider.goBack();
    }

    public boolean canGoForward() {
        checkThread();
        return mProvider.canGoForward();
    }

    public void goForward() {
        checkThread();
        mProvider.goForward();
    }

    public boolean canGoBackOrForward(int steps) {
        checkThread();
        return mProvider.canGoBackOrForward(steps);
    }

    public void goBackOrForward(int steps) {
        checkThread();
        mProvider.goBackOrForward(steps);
    }

    public boolean isPrivateBrowsingEnabled() {
        checkThread();
        return mProvider.isPrivateBrowsingEnabled();
    }

    public boolean pageUp(boolean top) {
        checkThread();
        return mProvider.pageUp(top);
    }

    public boolean pageDown(boolean bottom) {
        checkThread();
        return mProvider.pageDown(bottom);
    }

    public void postVisualStateCallback(long requestId, @NonNull VisualStateCallback callback) {
        checkThread();
        mProvider.insertVisualStateCallback(requestId, callback);
    }

    @Deprecated
    public void clearView() {
        checkThread();
        mProvider.clearView();
    }

    @Deprecated
    public Picture capturePicture() {
        checkThread();
        return mProvider.capturePicture();
    }

    @Deprecated
    public PrintDocumentAdapter createPrintDocumentAdapter() {
        checkThread();
        return mProvider.createPrintDocumentAdapter("default");
    }

    @NonNull
    public PrintDocumentAdapter createPrintDocumentAdapter(@NonNull String documentName) {
        checkThread();
        return mProvider.createPrintDocumentAdapter(documentName);
    }

    @Deprecated
    @ViewDebug.ExportedProperty(category = "webview")
    public float getScale() {
        checkThread();
        return mProvider.getScale();
    }

    public void setInitialScale(int scaleInPercent) {
        checkThread();
        mProvider.setInitialScale(scaleInPercent);
    }

    public void invokeZoomPicker() {
        checkThread();
        mProvider.invokeZoomPicker();
    }

    @NonNull
    public HitTestResult getHitTestResult() {
        checkThread();
        return mProvider.getHitTestResult();
    }

    public void requestFocusNodeHref(@Nullable Message hrefMsg) {
        checkThread();
        mProvider.requestFocusNodeHref(hrefMsg);
    }

    public void requestImageRef(@NonNull Message msg) {
        checkThread();
        mProvider.requestImageRef(msg);
    }

    @ViewDebug.ExportedProperty(category = "webview")
    @Nullable
    public String getUrl() {
        checkThread();
        return mProvider.getUrl();
    }

    @ViewDebug.ExportedProperty(category = "webview")
    @Nullable
    public String getOriginalUrl() {
        checkThread();
        return mProvider.getOriginalUrl();
    }

    @ViewDebug.ExportedProperty(category = "webview")
    @Nullable
    public String getTitle() {
        checkThread();
        return mProvider.getTitle();
    }

    @Nullable
    public Bitmap getFavicon() {
        checkThread();
        return mProvider.getFavicon();
    }

    public String getTouchIconUrl() {
        return mProvider.getTouchIconUrl();
    }

    public int getProgress() {
        checkThread();
        return mProvider.getProgress();
    }

    @ViewDebug.ExportedProperty(category = "webview")
    public int getContentHeight() {
        checkThread();
        return mProvider.getContentHeight();
    }

    @ViewDebug.ExportedProperty(category = "webview")
    public int getContentWidth() {
        return mProvider.getContentWidth();
    }

    public void pauseTimers() {
        checkThread();
        mProvider.pauseTimers();
    }

    public void resumeTimers() {
        checkThread();
        mProvider.resumeTimers();
    }

    public void onPause() {
        checkThread();
        mProvider.onPause();
    }

    public void onResume() {
        checkThread();
        mProvider.onResume();
    }

    public boolean isPaused() {
        return mProvider.isPaused();
    }

    @Deprecated
    public void freeMemory() {
        checkThread();
        mProvider.freeMemory();
    }

    public void clearCache(boolean includeDiskFiles) {
        checkThread();
        mProvider.clearCache(includeDiskFiles);
    }

    public void clearFormData() {
        checkThread();
        mProvider.clearFormData();
    }

    public void clearHistory() {
        checkThread();
        mProvider.clearHistory();
    }

    public void clearSslPreferences() {
        checkThread();
        mProvider.clearSslPreferences();
    }

    public static void clearClientCertPreferences(@Nullable Runnable onCleared) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public static void startSafeBrowsing(@NonNull Context context,
            @Nullable ValueCallback<Boolean> callback) {
        throw new RuntimeException("Stub!");
    }

    public static void setSafeBrowsingWhitelist(@NonNull List<String> hosts,
            @Nullable ValueCallback<Boolean> callback) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public static Uri getSafeBrowsingPrivacyPolicyUrl() {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public WebBackForwardList copyBackForwardList() {
        checkThread();
        return mProvider.copyBackForwardList();

    }

    public void setFindListener(@Nullable FindListener listener) {
        checkThread();
        setupFindListenerIfNeeded();
        mFindListener.mUserFindListener = listener;
    }

    public void findNext(boolean forward) {
        checkThread();
        mProvider.findNext(forward);
    }

    @Deprecated
    public int findAll(String find) {
        checkThread();
        StrictMode.noteSlowCall("findAll blocks UI: prefer findAllAsync");
        return mProvider.findAll(find);
    }

    public void findAllAsync(@NonNull String find) {
        checkThread();
        mProvider.findAllAsync(find);
    }

    @Deprecated
    public boolean showFindDialog(@Nullable String text, boolean showIme) {
        checkThread();
        return mProvider.showFindDialog(text, showIme);
    }

    @Nullable
    @Deprecated
    public static String findAddress(String addr) {
        throw new RuntimeException("Stub!");
    }

    public static void enableSlowWholeDocumentDraw() {
        throw new RuntimeException("Stub!");
    }

    public void clearMatches() {
        checkThread();
        mProvider.clearMatches();
    }

    public void documentHasImages(@NonNull Message response) {
        checkThread();
        mProvider.documentHasImages(response);
    }

    public void setWebViewClient(@NonNull WebViewClient client) {
        checkThread();
        mProvider.setWebViewClient(client);
    }

    @NonNull
    public WebViewClient getWebViewClient() {
        checkThread();
        return mProvider.getWebViewClient();
    }


    @Nullable
    public WebViewRenderProcess getWebViewRenderProcess() {
        checkThread();
        return mProvider.getWebViewRenderProcess();
    }

    public void setWebViewRenderProcessClient(
            @NonNull Executor executor,
            @NonNull WebViewRenderProcessClient webViewRenderProcessClient) {
        checkThread();
        mProvider.setWebViewRenderProcessClient(
                executor, webViewRenderProcessClient);
    }

    public void setWebViewRenderProcessClient(
            @Nullable WebViewRenderProcessClient webViewRenderProcessClient) {
        checkThread();
        mProvider.setWebViewRenderProcessClient(null, webViewRenderProcessClient);
    }

    @Nullable
    public WebViewRenderProcessClient getWebViewRenderProcessClient() {
        checkThread();
        return mProvider.getWebViewRenderProcessClient();
    }

    public void setDownloadListener(@Nullable DownloadListener listener) {
        checkThread();
        mProvider.setDownloadListener(listener);
    }

    public void setWebChromeClient(@Nullable WebChromeClient client) {
        checkThread();
        mProvider.setWebChromeClient(client);
    }

    @Nullable
    public WebChromeClient getWebChromeClient() {
        checkThread();
        return mProvider.getWebChromeClient();
    }

    @Deprecated
    public void setPictureListener(PictureListener listener) {
        checkThread();
        mProvider.setPictureListener(listener);
    }

    public void addJavascriptInterface(@NonNull Object object, @NonNull String name) {
        checkThread();
        mProvider.addJavascriptInterface(object, name);
    }

    public void removeJavascriptInterface(@NonNull String name) {
        checkThread();
        mProvider.removeJavascriptInterface(name);
    }

    @NonNull
    public WebMessagePort[] createWebMessageChannel() {
        checkThread();
        return mProvider.createWebMessageChannel();
    }

    public void postWebMessage(@NonNull WebMessage message, @NonNull Uri targetOrigin) {
        checkThread();
        mProvider.postMessageToMainFrame(message, targetOrigin);
    }

    @NonNull
    public WebSettings getSettings() {
        checkThread();
        return mProvider.getSettings();
    }

    public static void setWebContentsDebuggingEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public static void setDataDirectorySuffix(@NonNull String suffix) {
        throw new RuntimeException("Stub!");
    }

    public static void disableWebView() {
    }


    @Deprecated
    public void refreshPlugins(boolean reloadOpenPages) {
        checkThread();
    }

    @Deprecated
    public void emulateShiftHeld() {
        checkThread();
    }

    @Override
    // Cannot add @hide as this can always be accessed via the interface.
    @Deprecated
    public void onChildViewAdded(View parent, View child) {}

    @Override
    // Cannot add @hide as this can always be accessed via the interface.
    @Deprecated
    public void onChildViewRemoved(View p, View child) {}

    @Override
    // Cannot add @hide as this can always be accessed via the interface.
    @Deprecated
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
    }

    @Deprecated
    public void setMapTrackballToArrowKeys(boolean setMap) {
        checkThread();
        mProvider.setMapTrackballToArrowKeys(setMap);
    }


    public void flingScroll(int vx, int vy) {
        checkThread();
        mProvider.flingScroll(vx, vy);
    }

    @Deprecated
    public View getZoomControls() {
        checkThread();
        return mProvider.getZoomControls();
    }

    @Deprecated
    public boolean canZoomIn() {
        checkThread();
        return mProvider.canZoomIn();
    }

    @Deprecated
    public boolean canZoomOut() {
        checkThread();
        return mProvider.canZoomOut();
    }

    public void zoomBy(float zoomFactor) {
        checkThread();
        if (zoomFactor < 0.01)
            throw new IllegalArgumentException("zoomFactor must be greater than 0.01.");
        if (zoomFactor > 100.0)
            throw new IllegalArgumentException("zoomFactor must be less than 100.");
        mProvider.zoomBy(zoomFactor);
    }

    public boolean zoomIn() {
        checkThread();
        return mProvider.zoomIn();
    }

    public boolean zoomOut() {
        checkThread();
        return mProvider.zoomOut();
    }

    @Deprecated
    public void debugDump() {
        checkThread();
    }

    public void dumpViewHierarchyWithProperties(BufferedWriter out, int level) {
        mProvider.dumpViewHierarchyWithProperties(out, level);
    }

    public View findHierarchyView(String className, int hashCode) {
        return mProvider.findHierarchyView(className, hashCode);
    }

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface RendererPriority {}

    public static final int RENDERER_PRIORITY_IMPORTANT = 2;

    public void setRendererPriorityPolicy(
            @RendererPriority int rendererRequestedPriority,
            boolean waivedWhenNotVisible) {
        mProvider.setRendererPriorityPolicy(rendererRequestedPriority, waivedWhenNotVisible);
    }

    @RendererPriority
    public int getRendererRequestedPriority() {
        return mProvider.getRendererRequestedPriority();
    }

    public boolean getRendererPriorityWaivedWhenNotVisible() {
        return mProvider.getRendererPriorityWaivedWhenNotVisible();
    }

    public void setTextClassifier(@Nullable TextClassifier textClassifier) {
        mProvider.setTextClassifier(textClassifier);
    }

    @NonNull
    public TextClassifier getTextClassifier() {
        return mProvider.getTextClassifier();
    }

    @NonNull
    public static ClassLoader getWebViewClassLoader() {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public Looper getWebViewLooper() {
        return mWebViewThread;
    }

    //-------------------------------------------------------------------------
    // Interface for WebView providers
    //-------------------------------------------------------------------------

    @SystemApi
    public WebViewProvider getWebViewProvider() {
        return mProvider;
    }

    //-------------------------------------------------------------------------
    // Package-private internal stuff
    //-------------------------------------------------------------------------

    // Only used by android.webkit.FindActionModeCallback.
    void setFindDialogFindListener(FindListener listener) {
        checkThread();
        setupFindListenerIfNeeded();
        mFindListener.mFindDialogFindListener = listener;
    }

    // Only used by android.webkit.FindActionModeCallback.
    void notifyFindDialogDismissed() {
        checkThread();
        mProvider.notifyFindDialogDismissed();
    }

    //-------------------------------------------------------------------------
    // Private internal stuff
    //-------------------------------------------------------------------------

    private WebViewProvider mProvider;

    private class FindListenerDistributor implements FindListener {
        private FindListener mFindDialogFindListener;
        private FindListener mUserFindListener;

        @Override
        public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
                boolean isDoneCounting) {
            if (mFindDialogFindListener != null) {
                mFindDialogFindListener.onFindResultReceived(activeMatchOrdinal, numberOfMatches,
                        isDoneCounting);
            }

            if (mUserFindListener != null) {
                mUserFindListener.onFindResultReceived(activeMatchOrdinal, numberOfMatches,
                        isDoneCounting);
            }
        }
    }
    private FindListenerDistributor mFindListener;

    private void setupFindListenerIfNeeded() {
        if (mFindListener == null) {
            mFindListener = new FindListenerDistributor();
            mProvider.setFindListener(mFindListener);
        }
    }

    private void ensureProviderCreated() {
        checkThread();
        if (mProvider == null) {
            // As this can get called during the base class constructor chain, pass the minimum
            // number of dependencies here; the rest are deferred to init().
            mProvider = new PlaywrightWebViewProvider(this);
        }
    }

    private final Looper mWebViewThread = Looper.myLooper();

    private void checkThread() {
        // Ignore mWebViewThread == null because this can be called during in the super class
        // constructor, before this class's own constructor has even started.
        if (mWebViewThread != null && Looper.myLooper() != mWebViewThread) {
            Throwable throwable = new Throwable(
                    "A WebView method was called on thread '" +
                    Thread.currentThread().getName() + "'. " +
                    "All WebView methods must be called on the same thread. " +
                    "(Expected Looper " + mWebViewThread + " called on " + Looper.myLooper() +
                    ", FYI main Looper is " + Looper.getMainLooper() + ")");
            Log.w(LOGTAG, Log.getStackTraceString(throwable));

            if (sEnforceThreadChecking) {
                throw new RuntimeException(throwable);
            }
        }
    }

    //-------------------------------------------------------------------------
    // Override View methods
    //-------------------------------------------------------------------------

    // TODO: Add a test that enumerates all methods in ViewDelegte & ScrollDelegate, and ensures
    // there's a corresponding override (or better, caller) for each of them in here.

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mProvider.getViewDelegate().onAttachedToWindow();
    }

    // /** @hide */
    // protected void onDetachedFromWindowInternal() {
    //     mProvider.getViewDelegate().onDetachedFromWindow();
    //     super.onDetachedFromWindowInternal();
    // }

    /** @hide */
    public void onMovedToDisplay(int displayId, Configuration config) {
        mProvider.getViewDelegate().onMovedToDisplay(displayId, config);
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        mProvider.getViewDelegate().setLayoutParams(params);
    }

    @Override
    public void setOverScrollMode(int mode) {
        super.setOverScrollMode(mode);
        // This method may be called in the constructor chain, before the WebView provider is
        // created.
        ensureProviderCreated();
        mProvider.getViewDelegate().setOverScrollMode(mode);
    }

    @Override
    public void setScrollBarStyle(int style) {
        mProvider.getViewDelegate().setScrollBarStyle(style);
        super.setScrollBarStyle(style);
    }

    @Override
    protected int computeHorizontalScrollRange() {
        return mProvider.getScrollDelegate().computeHorizontalScrollRange();
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return mProvider.getScrollDelegate().computeHorizontalScrollOffset();
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mProvider.getScrollDelegate().computeVerticalScrollRange();
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mProvider.getScrollDelegate().computeVerticalScrollOffset();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return mProvider.getScrollDelegate().computeVerticalScrollExtent();
    }

    @Override
    public void computeScroll() {
        mProvider.getScrollDelegate().computeScroll();
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return mProvider.getViewDelegate().onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mProvider.getViewDelegate().onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mProvider.getViewDelegate().onGenericMotionEvent(event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        return mProvider.getViewDelegate().onTrackballEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mProvider.getViewDelegate().onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mProvider.getViewDelegate().onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        return mProvider.getViewDelegate().onKeyMultiple(keyCode, repeatCount, event);
    }

    /*
    TODO: These are not currently implemented in WebViewClassic, but it seems inconsistent not
    to be delegating them too.

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        return mProvider.getViewDelegate().onKeyPreIme(keyCode, event);
    }
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return mProvider.getViewDelegate().onKeyLongPress(keyCode, event);
    }
    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        return mProvider.getViewDelegate().onKeyShortcut(keyCode, event);
    }
    */

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        AccessibilityNodeProvider provider =
                mProvider.getViewDelegate().getAccessibilityNodeProvider();
        return provider == null ? super.getAccessibilityNodeProvider() : provider;
    }

    @Deprecated
    @Override
    public boolean shouldDelayChildPressedState() {
        return mProvider.getViewDelegate().shouldDelayChildPressedState();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return WebView.class.getName();
    }

    @Override
    public void onProvideVirtualStructure(ViewStructure structure) {
        mProvider.getViewDelegate().onProvideVirtualStructure(structure);
    }

    @Override
    public void onProvideAutofillVirtualStructure(ViewStructure structure, int flags) {
        mProvider.getViewDelegate().onProvideAutofillVirtualStructure(structure, flags);
    }

    @Override
    public void onProvideContentCaptureStructure(@NonNull ViewStructure structure, int flags) {
        mProvider.getViewDelegate().onProvideContentCaptureStructure(structure, flags);
    }

    @Override
    public void autofill(SparseArray<AutofillValue>values) {
        mProvider.getViewDelegate().autofill(values);
    }

    @Override
    public boolean isVisibleToUserForAutofill(int virtualId) {
        return mProvider.getViewDelegate().isVisibleToUserForAutofill(virtualId);
    }

    // @Nullable
    // public void onCreateVirtualViewTranslationRequests(@NonNull long[] virtualIds,
    //         @NonNull int[] supportedFormats,
    //         @NonNull Consumer<ViewTranslationRequest> requestsCollector) {
    //     mProvider.getViewDelegate().onCreateVirtualViewTranslationRequests(virtualIds,
    //             supportedFormats, requestsCollector);
    // }

    // public void dispatchCreateViewTranslationRequest(@NonNull Map<AutofillId, long[]> viewIds,
    //         @NonNull int[] supportedFormats,
    //         @Nullable TranslationCapability capability,
    //         @NonNull List<ViewTranslationRequest> requests) {
    //     super.dispatchCreateViewTranslationRequest(viewIds, supportedFormats, capability, requests);
    //     mProvider.getViewDelegate().dispatchCreateViewTranslationRequest(viewIds, supportedFormats,
    //             capability, requests);
    // }

    // public void onVirtualViewTranslationResponses(
    //         @NonNull LongSparseArray<ViewTranslationResponse> response) {
    //     mProvider.getViewDelegate().onVirtualViewTranslationResponses(response);
    // }

    // /** @hide */
    // public void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
    //     super.onInitializeAccessibilityNodeInfoInternal(info);
    //     mProvider.getViewDelegate().onInitializeAccessibilityNodeInfo(info);
    // }

    // /** @hide */
    // public void onInitializeAccessibilityEventInternal(AccessibilityEvent event) {
    //     super.onInitializeAccessibilityEventInternal(event);
    //     mProvider.getViewDelegate().onInitializeAccessibilityEvent(event);
    // }

    /** @hide */
    public boolean performAccessibilityActionInternal(int action, Bundle arguments) {
        return mProvider.getViewDelegate().performAccessibilityAction(action, arguments);
    }

    /** @hide */
    protected void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar,
            int l, int t, int r, int b) {
        mProvider.getViewDelegate().onDrawVerticalScrollBar(canvas, scrollBar, l, t, r, b);
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        mProvider.getViewDelegate().onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mProvider.getViewDelegate().onWindowVisibilityChanged(visibility);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mProvider.getViewDelegate().onDraw(canvas);
    }

    @Override
    public boolean performLongClick() {
        return mProvider.getViewDelegate().performLongClick();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        mProvider.getViewDelegate().onConfigurationChanged(newConfig);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return mProvider.getViewDelegate().onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        return mProvider.getViewDelegate().onDragEvent(event);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // This method may be called in the constructor chain, before the WebView provider is
        // created.
        ensureProviderCreated();
        mProvider.getViewDelegate().onVisibilityChanged(changedView, visibility);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        mProvider.getViewDelegate().onWindowFocusChanged(hasWindowFocus);
        super.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        mProvider.getViewDelegate().onFocusChanged(focused, direction, previouslyFocusedRect);
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /** @hide */
    protected boolean setFrame(int left, int top, int right, int bottom) {
        return mProvider.getViewDelegate().setFrame(left, top, right, bottom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        mProvider.getViewDelegate().onSizeChanged(w, h, ow, oh);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mProvider.getViewDelegate().onScrollChanged(l, t, oldl, oldt);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mProvider.getViewDelegate().dispatchKeyEvent(event);
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        return mProvider.getViewDelegate().requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mProvider.getViewDelegate().onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        return mProvider.getViewDelegate().requestChildRectangleOnScreen(child, rect, immediate);
    }

    @Override
    public void setBackgroundColor(int color) {
        mProvider.getViewDelegate().setBackgroundColor(color);
    }

    @Override
    public void setLayerType(int layerType, Paint paint) {
        super.setLayerType(layerType, paint);
        mProvider.getViewDelegate().setLayerType(layerType, paint);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mProvider.getViewDelegate().preDispatchDraw(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    public void onStartTemporaryDetach() {
        super.onStartTemporaryDetach();
        mProvider.getViewDelegate().onStartTemporaryDetach();
    }

    @Override
    public void onFinishTemporaryDetach() {
        super.onFinishTemporaryDetach();
        mProvider.getViewDelegate().onFinishTemporaryDetach();
    }

    @Override
    public Handler getHandler() {
        return mProvider.getViewDelegate().getHandler(super.getHandler());
    }

    @Override
    public View findFocus() {
        return mProvider.getViewDelegate().findFocus(super.findFocus());
    }

    @Nullable
    public static PackageInfo getCurrentWebViewPackage() {
        throw new RuntimeException("Stub!");
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mProvider.getViewDelegate().onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return mProvider.getViewDelegate().onCheckIsTextEditor();
    }

    // /** @hide */
    // protected void encodeProperties(@NonNull ViewHierarchyEncoder encoder) {
    //     super.encodeProperties(encoder);

    //     checkThread();
    //     encoder.addProperty("webview:contentHeight", mProvider.getContentHeight());
    //     encoder.addProperty("webview:contentWidth", mProvider.getContentWidth());
    //     encoder.addProperty("webview:scale", mProvider.getScale());
    //     encoder.addProperty("webview:title", mProvider.getTitle());
    //     encoder.addProperty("webview:url", mProvider.getUrl());
    //     encoder.addProperty("webview:originalUrl", mProvider.getOriginalUrl());
    // }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        WindowInsets result = mProvider.getViewDelegate().onApplyWindowInsets(insets);
        if (result == null) return super.onApplyWindowInsets(insets);
        return result;
    }

    @Override
    @Nullable
    public PointerIcon onResolvePointerIcon(@NonNull MotionEvent event, int pointerIndex) {
        PointerIcon icon =
                mProvider.getViewDelegate().onResolvePointerIcon(event, pointerIndex);
        if (icon != null) {
            return icon;
        }
        return super.onResolvePointerIcon(event, pointerIndex);
    }
}
