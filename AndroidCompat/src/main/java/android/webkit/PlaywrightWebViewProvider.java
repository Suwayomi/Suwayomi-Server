/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.print.PrintDocumentAdapter;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textclassifier.TextClassifier;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebView.PictureListener;
import android.webkit.WebView.VisualStateCallback;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.BufferedWriter;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class PlaywrightWebViewProvider implements WebViewProvider {
    @Nullable Playwright _playwright;
    @Nullable Browser _browser;
    @Nullable BrowserContext _context;
    @Nullable Page _page;

    WebView _view;
    PlaywrightWebSettings _settings;
    WebViewClient _viewClient;
    WebChromeClient _chromeClient;
    List<FunctionMapping> _mappings;

    Looper _looper;
    Handler _handler;

    private static final String TAG = "PlaywrightWebViewProvider";

    private static String BROWSER_TYPE = "chromium";
    private static String BROWSER_CONNECT = "";
    private static Boolean BROWSER_SANDBOX = true;

    public PlaywrightWebViewProvider(WebView view) {
        _view = view;
        _settings = new PlaywrightWebSettings();
        _viewClient = new WebViewClient();
        _chromeClient = new WebChromeClient();
        _mappings = new ArrayList<FunctionMapping>();
        _looper = Looper.myLooper();
        _handler = new Handler(_looper);
    }

    public void init(Map<String, Object> javaScriptInterfaces,
            boolean privateBrowsing) {
        destroy();
        _playwright = Playwright.create();
        BrowserType b = getBrowserType(_playwright, BROWSER_TYPE);
        if (BROWSER_CONNECT == null || BROWSER_CONNECT.equals("")) {
            _browser = b.launch(
                new BrowserType.LaunchOptions()
                    .setChromiumSandbox(BROWSER_SANDBOX)
                    .setHeadless(true)
            );
        } else {
            _browser = b.connect(BROWSER_CONNECT, new BrowserType.ConnectOptions().setTimeout(5000));
        }
    }

    public static void setBrowserType(String type) {
        BROWSER_TYPE = type;
    }

    public static void setBrowserConnect(String url) {
        BROWSER_CONNECT = url;
    }

    public static void setBrowserSandbox(Boolean value) {
        BROWSER_SANDBOX = value;
    }

    // Deprecated - should never be called
    public void setHorizontalScrollbarOverlay(boolean overlay) {
        throw new RuntimeException("Stub!");
    }

    // Deprecated - should never be called
    public void setVerticalScrollbarOverlay(boolean overlay) {
        throw new RuntimeException("Stub!");
    }

    // Deprecated - should never be called
    public boolean overlayHorizontalScrollbar() {
        throw new RuntimeException("Stub!");
    }

    // Deprecated - should never be called
    public boolean overlayVerticalScrollbar() {
        throw new RuntimeException("Stub!");
    }

    public int getVisibleTitleHeight() {
        throw new RuntimeException("Stub!");
    }

    public SslCertificate getCertificate() {
        throw new RuntimeException("Stub!");
    }

    public void setCertificate(SslCertificate certificate) {
        throw new RuntimeException("Stub!");
    }

    public void savePassword(String host, String username, String password) {
        throw new RuntimeException("Stub!");
    }

    public void setHttpAuthUsernamePassword(String host, String realm,
            String username, String password) {
        throw new RuntimeException("Stub!");
    }

    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        throw new RuntimeException("Stub!");
    }

    public void destroy() {
        if (_page != null) {
            _page.close();
            _page = null;
        }
        if (_context != null) {
            _context.close();
            _context = null;
        }
        if (_browser != null) {
            _browser.close();
            _browser = null;
        }
        if (_playwright != null) {
            _playwright.close();
            _playwright = null;
        }
    }

    public void setNetworkAvailable(boolean networkUp) {
        throw new RuntimeException("Stub!");
    }

    public WebBackForwardList saveState(Bundle outState) {
        throw new RuntimeException("Stub!");
    }

    public boolean savePicture(Bundle b, final File dest) {
        throw new RuntimeException("Stub!");
    }

    public boolean restorePicture(Bundle b, File src) {
        throw new RuntimeException("Stub!");
    }

    public WebBackForwardList restoreState(Bundle inState) {
        throw new RuntimeException("Stub!");
    }

    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        ensurePage();
        _page.setExtraHTTPHeaders(additionalHttpHeaders);
        try {
            _chromeClient.onProgressChanged(_view, 0);
            Response response = _page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
            if (response != null) {
                if (response.status() == 404) {
                    _viewClient.onReceivedError(_view, WebViewClient.ERROR_FILE_NOT_FOUND, response.statusText(), url);
                }
                if (response.status() == 429) {
                    _viewClient.onReceivedError(_view, WebViewClient.ERROR_TOO_MANY_REQUESTS, response.statusText(), url);
                }
                if (response.status() >= 400) {
                    // TODO: create request and response
                    // _viewClient.onReceivedHttpError(_view, ...);
                }
            }
            Log.d(TAG, "Page loaded at URL " + url);
            _handler.post(() -> pumpMessageLoop());
        } catch (Exception e) {
            Log.w(TAG, "Exception while loading URL " + url, e);
            // TODO: translate correctly
            _viewClient.onReceivedError(_view, WebViewClient.ERROR_UNKNOWN, e.getMessage(), url);
        }
    }

    private void pumpMessageLoop() {
        // pump Playwright's message loop every 100ms
        if (_page == null) return;
        _page.waitForTimeout(1);
        _handler.postDelayed(() -> pumpMessageLoop(), 100);
    }

    public void loadUrl(String url) {
        loadUrl(url, new HashMap<String, String>());
    }

    public void postUrl(String url, byte[] postData) {
        throw new RuntimeException("Stub!");
    }

    public void loadData(String data, String mimeType, String encoding) {
        loadDataWithBaseURL(null, data, mimeType, encoding, null);
    }

    public void loadDataWithBaseURL(String baseUrl, String data,
            String mimeType, String encoding, String historyUrl) {
        ensurePage();
        try {
            _page.setContent(data, new Page.SetContentOptions().setWaitUntil(WaitUntilState.LOAD));
            Log.d(TAG, "Page loaded from content at base URL " + baseUrl);
        } catch (Exception e) {
            Log.w(TAG, "Exception while loading content at base URL " + baseUrl, e);
            // TODO: translate correctly
            _viewClient.onReceivedError(_view, WebViewClient.ERROR_UNKNOWN, e.getMessage(), baseUrl);
        }
    }

    public void evaluateJavaScript(String script, ValueCallback<String> resultCallback) {
        // TODO: This should be async
        if (_page == null) {
            throw new IllegalStateException("JS evaluate before page creation, load a page first");
        }
        Object res = _page.evaluate(script);
        resultCallback.onReceiveValue(res != null ? res.toString() : null);
    }

    public void saveWebArchive(String filename) {
        throw new RuntimeException("Stub!");
    }

    public void saveWebArchive(String basename, boolean autoname, ValueCallback<String> callback) {
        throw new RuntimeException("Stub!");
    }

    public void stopLoading() {
        if (_page != null) {
            _page.close();
        }
    }

    public void reload() {
        throw new RuntimeException("Stub!");
    }

    public boolean canGoBack() {
        throw new RuntimeException("Stub!");
    }

    public void goBack() {
        throw new RuntimeException("Stub!");
    }

    public boolean canGoForward() {
        throw new RuntimeException("Stub!");
    }

    public void goForward() {
        throw new RuntimeException("Stub!");
    }

    public boolean canGoBackOrForward(int steps) {
        throw new RuntimeException("Stub!");
    }

    public void goBackOrForward(int steps) {
        throw new RuntimeException("Stub!");
    }

    public boolean isPrivateBrowsingEnabled() {
        throw new RuntimeException("Stub!");
    }

    public boolean pageUp(boolean top) {
        throw new RuntimeException("Stub!");
    }

    public boolean pageDown(boolean bottom) {
        throw new RuntimeException("Stub!");
    }

    public void insertVisualStateCallback(long requestId, VisualStateCallback callback) {
        throw new RuntimeException("Stub!");
    }

    public void clearView() {
        throw new RuntimeException("Stub!");
    }

    public Picture capturePicture() {
        throw new RuntimeException("Stub!");
    }

    public PrintDocumentAdapter createPrintDocumentAdapter(String documentName) {
        throw new RuntimeException("Stub!");
    }

    public float getScale() {
        throw new RuntimeException("Stub!");
    }

    public void setInitialScale(int scaleInPercent) {
        throw new RuntimeException("Stub!");
    }

    public void invokeZoomPicker() {
        throw new RuntimeException("Stub!");
    }

    public HitTestResult getHitTestResult() {
        throw new RuntimeException("Stub!");
    }

    public void requestFocusNodeHref(Message hrefMsg) {
        throw new RuntimeException("Stub!");
    }

    public void requestImageRef(Message msg) {
        throw new RuntimeException("Stub!");
    }

    public String getUrl() {
        throw new RuntimeException("Stub!");
    }

    public String getOriginalUrl() {
        throw new RuntimeException("Stub!");
    }

    public String getTitle() {
        throw new RuntimeException("Stub!");
    }

    public Bitmap getFavicon() {
        throw new RuntimeException("Stub!");
    }

    public String getTouchIconUrl() {
        throw new RuntimeException("Stub!");
    }

    public int getProgress() {
        throw new RuntimeException("Stub!");
    }

    public int getContentHeight() {
        throw new RuntimeException("Stub!");
    }

    public int getContentWidth() {
        throw new RuntimeException("Stub!");
    }

    public void pauseTimers() {
        throw new RuntimeException("Stub!");
    }

    public void resumeTimers() {
        throw new RuntimeException("Stub!");
    }

    public void onPause() {
        throw new RuntimeException("Stub!");
    }

    public void onResume() {
        throw new RuntimeException("Stub!");
    }

    public boolean isPaused() {
        throw new RuntimeException("Stub!");
    }

    public void freeMemory() {
        throw new RuntimeException("Stub!");
    }

    public void clearCache(boolean includeDiskFiles) {
        throw new RuntimeException("Stub!");
    }

    public void clearFormData() {
        throw new RuntimeException("Stub!");
    }

    public void clearHistory() {
        throw new RuntimeException("Stub!");
    }

    public void clearSslPreferences() {
        throw new RuntimeException("Stub!");
    }

    public WebBackForwardList copyBackForwardList() {
        throw new RuntimeException("Stub!");
    }

    public void setFindListener(WebView.FindListener listener) {
        throw new RuntimeException("Stub!");
    }

    public void findNext(boolean forward) {
        throw new RuntimeException("Stub!");
    }

    public int findAll(String find) {
        throw new RuntimeException("Stub!");
    }

    public void findAllAsync(String find) {
        throw new RuntimeException("Stub!");
    }

    public boolean showFindDialog(String text, boolean showIme) {
        throw new RuntimeException("Stub!");
    }

    public void clearMatches() {
        throw new RuntimeException("Stub!");
    }

    public void documentHasImages(Message response) {
        throw new RuntimeException("Stub!");
    }

    public void setWebViewClient(WebViewClient client) {
        _viewClient = client;
    }

    public WebViewClient getWebViewClient() {
        return _viewClient;
    }

    @Nullable
    public WebViewRenderProcess getWebViewRenderProcess() {
        throw new RuntimeException("Stub!");
    }

    public void setWebViewRenderProcessClient(
            @Nullable Executor executor,
            @Nullable WebViewRenderProcessClient client) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public WebViewRenderProcessClient getWebViewRenderProcessClient() {
        throw new RuntimeException("Stub!");
    }

    public void setDownloadListener(DownloadListener listener) {
        throw new RuntimeException("Stub!");
    }

    public void setWebChromeClient(WebChromeClient client) {
        _chromeClient = client;
    }

    public WebChromeClient getWebChromeClient() {
        return _chromeClient;
    }

    public void setPictureListener(PictureListener listener) {
        throw new RuntimeException("Stub!");
    }

    private static class FunctionMapping {
        public final String interfaceName;
        public final String functionName;

        public FunctionMapping(String ifn, String fn) {
            interfaceName = ifn;
            functionName = fn;
        }

        public String toExposed() {
            return "__$_" + this.interfaceName + "|" + this.functionName;
        }

        public String toNice() {
            return this.interfaceName + "." + this.functionName;
        }
    }

    public void addJavascriptInterface(Object obj, String interfaceName) {
        ensurePage();
        Class cls = obj.getClass();
        Method methodlist[] = cls.getDeclaredMethods();
        for (Method method : methodlist) {
            FunctionMapping map = new FunctionMapping(interfaceName, method.getName());
            _context.exposeFunction(map.toExposed(), args -> {
                try {
                    return method.invoke(obj, args);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to invoke interface method " + map.toNice(), e);
                    return null;
                }
            });
            Log.v(TAG, "Exposing: " + map.toNice());
            _mappings.add(map);
        }
    }

    public void removeJavascriptInterface(String interfaceName) {
        throw new RuntimeException("Stub!");
    }

    public WebMessagePort[] createWebMessageChannel() {
        throw new RuntimeException("Stub!");
    }

    public void postMessageToMainFrame(WebMessage message, Uri targetOrigin) {
        throw new RuntimeException("Stub!");
    }

    public WebSettings getSettings() {
        return _settings;
    }

    public void setMapTrackballToArrowKeys(boolean setMap) {
        throw new RuntimeException("Stub!");
    }

    public void flingScroll(int vx, int vy) {
        throw new RuntimeException("Stub!");
    }

    public View getZoomControls() {
        throw new RuntimeException("Stub!");
    }

    public boolean canZoomIn() {
        throw new RuntimeException("Stub!");
    }

    public boolean canZoomOut() {
        throw new RuntimeException("Stub!");
    }

    public boolean zoomBy(float zoomFactor) {
        throw new RuntimeException("Stub!");
    }

    public boolean zoomIn() {
        throw new RuntimeException("Stub!");
    }

    public boolean zoomOut() {
        throw new RuntimeException("Stub!");
    }

    public void dumpViewHierarchyWithProperties(BufferedWriter out, int level) {
        throw new RuntimeException("Stub!");
    }

    public View findHierarchyView(String className, int hashCode) {
        throw new RuntimeException("Stub!");
    }

    public void setRendererPriorityPolicy(int rendererRequestedPriority, boolean waivedWhenNotVisible) {
        throw new RuntimeException("Stub!");
    }

    public int getRendererRequestedPriority() {
        throw new RuntimeException("Stub!");
    }

    public boolean getRendererPriorityWaivedWhenNotVisible() {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings("unused")
    public void setTextClassifier(@Nullable TextClassifier textClassifier) {}

    @NonNull
    public TextClassifier getTextClassifier() { return TextClassifier.NO_OP; }

    //-------------------------------------------------------------------------
    // Provider internal methods
    //-------------------------------------------------------------------------

    public ViewDelegate getViewDelegate() {
        throw new RuntimeException("Stub!");
    }

    public ScrollDelegate getScrollDelegate() {
        throw new RuntimeException("Stub!");
    }

    public void notifyFindDialogDismissed() {
        throw new RuntimeException("Stub!");
    }

    //-------------------------------------------------------------------------
    // View / ViewGroup delegation methods
    //-------------------------------------------------------------------------

    public class PlaywrightViewDelegate implements ViewDelegate {
        public boolean shouldDelayChildPressedState() {
            throw new RuntimeException("Stub!");
        }

        public void onProvideVirtualStructure(android.view.ViewStructure structure) {
            throw new RuntimeException("Stub!");
        }

        public void onProvideAutofillVirtualStructure(
                @SuppressWarnings("unused") android.view.ViewStructure structure,
                @SuppressWarnings("unused") int flags) {
        }

        public void autofill(@SuppressWarnings("unused") SparseArray<AutofillValue> values) {
        }

        public boolean isVisibleToUserForAutofill(@SuppressWarnings("unused") int virtualId) {
            return true; // true is the default value returned by View.isVisibleToUserForAutofill()
        }

        public void onProvideContentCaptureStructure(
                @NonNull @SuppressWarnings("unused") android.view.ViewStructure structure,
                @SuppressWarnings("unused") int flags) {
        }

        // @SuppressLint("NullableCollection")
        // public void onCreateVirtualViewTranslationRequests(
        //         @NonNull @SuppressWarnings("unused") long[] virtualIds,
        //         @NonNull @SuppressWarnings("unused") @DataFormat int[] supportedFormats,
        //         @NonNull @SuppressWarnings("unused")
        //                 Consumer<ViewTranslationRequest> requestsCollector) {
        // }

        // public void onVirtualViewTranslationResponses(
        //         @NonNull @SuppressWarnings("unused")
        //                 LongSparseArray<ViewTranslationResponse> response) {
        // }

        // public void dispatchCreateViewTranslationRequest(
        //         @NonNull @SuppressWarnings("unused") Map<AutofillId, long[]> viewIds,
        //         @NonNull @SuppressWarnings("unused") @DataFormat int[] supportedFormats,
        //         @Nullable @SuppressWarnings("unused") TranslationCapability capability,
        //         @NonNull @SuppressWarnings("unused") List<ViewTranslationRequest> requests) {

        // }

        public AccessibilityNodeProvider getAccessibilityNodeProvider() {
            throw new RuntimeException("Stub!");
        }

        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            throw new RuntimeException("Stub!");
        }

        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            throw new RuntimeException("Stub!");
        }

        public boolean performAccessibilityAction(int action, Bundle arguments) {
            throw new RuntimeException("Stub!");
        }

        public void setOverScrollMode(int mode) {
            throw new RuntimeException("Stub!");
        }

        public void setScrollBarStyle(int style) {
            throw new RuntimeException("Stub!");
        }

        public void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar, int l, int t,
                int r, int b) {
            throw new RuntimeException("Stub!");
        }

        public void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
            throw new RuntimeException("Stub!");
        }

        public void onWindowVisibilityChanged(int visibility) {
            throw new RuntimeException("Stub!");
        }

        public void onDraw(Canvas canvas) {
            throw new RuntimeException("Stub!");
        }

        public void setLayoutParams(LayoutParams layoutParams) {
            throw new RuntimeException("Stub!");
        }

        public boolean performLongClick() {
            throw new RuntimeException("Stub!");
        }

        public void onConfigurationChanged(Configuration newConfig) {
            throw new RuntimeException("Stub!");
        }

        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            throw new RuntimeException("Stub!");
        }

        public boolean onDragEvent(DragEvent event) {
            throw new RuntimeException("Stub!");
        }

        public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
            throw new RuntimeException("Stub!");
        }

        public boolean onKeyDown(int keyCode, KeyEvent event) {
            throw new RuntimeException("Stub!");
        }

        public boolean onKeyUp(int keyCode, KeyEvent event) {
            throw new RuntimeException("Stub!");
        }

        public void onAttachedToWindow() {
            throw new RuntimeException("Stub!");
        }

        public void onDetachedFromWindow() {
            throw new RuntimeException("Stub!");
        }

        public void onMovedToDisplay(int displayId, Configuration config) {}

        public void onVisibilityChanged(View changedView, int visibility) {
            throw new RuntimeException("Stub!");
        }

        public void onWindowFocusChanged(boolean hasWindowFocus) {
            throw new RuntimeException("Stub!");
        }

        public void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
            throw new RuntimeException("Stub!");
        }

        public boolean setFrame(int left, int top, int right, int bottom) {
            throw new RuntimeException("Stub!");
        }

        public void onSizeChanged(int w, int h, int ow, int oh) {
            throw new RuntimeException("Stub!");
        }

        public void onScrollChanged(int l, int t, int oldl, int oldt) {
            throw new RuntimeException("Stub!");
        }

        public boolean dispatchKeyEvent(KeyEvent event) {
            throw new RuntimeException("Stub!");
        }

        public boolean onTouchEvent(MotionEvent ev) {
            throw new RuntimeException("Stub!");
        }

        public boolean onHoverEvent(MotionEvent event) {
            throw new RuntimeException("Stub!");
        }

        public boolean onGenericMotionEvent(MotionEvent event) {
            throw new RuntimeException("Stub!");
        }

        public boolean onTrackballEvent(MotionEvent ev) {
            throw new RuntimeException("Stub!");
        }

        public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
            throw new RuntimeException("Stub!");
        }

        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            throw new RuntimeException("Stub!");
        }

        public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
            throw new RuntimeException("Stub!");
        }

        public void setBackgroundColor(int color) {
            throw new RuntimeException("Stub!");
        }

        public void setLayerType(int layerType, Paint paint) {
            // ignore
        }

        public void preDispatchDraw(Canvas canvas) {
            throw new RuntimeException("Stub!");
        }

        public void onStartTemporaryDetach() {
            throw new RuntimeException("Stub!");
        }

        public void onFinishTemporaryDetach() {
            throw new RuntimeException("Stub!");
        }

        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            throw new RuntimeException("Stub!");
        }

        public Handler getHandler(Handler originalHandler) {
            throw new RuntimeException("Stub!");
        }

        public View findFocus(View originalFocusedView) {
            throw new RuntimeException("Stub!");
        }

        @SuppressWarnings("unused")
        public boolean onCheckIsTextEditor() {
            return false;
        }

        @SuppressWarnings("unused")
        @Nullable
        public WindowInsets onApplyWindowInsets(@Nullable WindowInsets insets) {
            return null;
        }

        @SuppressWarnings("unused")
        @Nullable
        public PointerIcon onResolvePointerIcon(@NonNull MotionEvent event, int pointerIndex) {
            return null;
        }
    }

    public class PlaywrightScrollDelegate implements ScrollDelegate {
        public int computeHorizontalScrollRange() {
            throw new RuntimeException("Stub!");
        }

        public int computeHorizontalScrollOffset() {
            throw new RuntimeException("Stub!");
        }

        public int computeVerticalScrollRange() {
            throw new RuntimeException("Stub!");
        }

        public int computeVerticalScrollOffset() {
            throw new RuntimeException("Stub!");
        }

        public int computeVerticalScrollExtent() {
            throw new RuntimeException("Stub!");
        }

        public void computeScroll() {
            throw new RuntimeException("Stub!");
        }
    }

    private static BrowserType getBrowserType(Playwright playwright, String type) {
        type = type.toLowerCase();
        switch (type) {
            case "chromium":
                return playwright.chromium();
            case "firefox":
                return playwright.firefox();
            case "webkit":
                return playwright.webkit();
            default:
                throw new RuntimeException("Invalid browser type specified: " + type);
        }
    }

    private static class PlaywrightWebResourceRequest implements WebResourceRequest {
        private Route _route;

        private PlaywrightWebResourceRequest(Route r) {
            _route = r;
        }

        public Uri getUrl() {
            return Uri.parse(_route.request().url());
        }

        public boolean isForMainFrame() {
            return true;
        }

        public boolean isRedirect() {
            return _route.request().redirectedFrom() != null;
        }

        public boolean hasGesture() {
            return false;
        }

        public String getMethod() {
            return _route.request().method();
        }

        public Map<String, String> getRequestHeaders() {
            return _route.request().headers();
        }
    }

    private void ensurePage() {
        if (_context == null) {
            _context = _browser.newContext(_settings.getBrowserOptions());
        }
        if (_page == null) {
            _page = _context.newPage();
            _page.onFrameNavigated(p -> {
                // rebind all mappins, expose still make them persistent, but we need to match what WebView did
                for (FunctionMapping map : _mappings) {
                    p.evaluate("([i, n, e]) => (window[i] = window[i] || {})[n] = window[e]",
                        Arrays.asList(map.interfaceName, map.functionName, map.toExposed()));
                }

                _viewClient.onPageStarted(_view, p.url(), null);
            });
            _page.onDOMContentLoaded(p -> _viewClient.onPageCommitVisible(_view, p.url()));
            _page.onLoad(p -> {
                _viewClient.onPageFinished(_view, p.url());
                _chromeClient.onProgressChanged(_view, 100);
                _chromeClient.onReceivedTitle(_view, p.title());
            });
            _page.onFrameNavigated(p -> Log.v(TAG, "Navigate: " + p.url()));
            _page.onDOMContentLoaded(p -> Log.v(TAG, "DOM Loaded: " + p.url()));
            _page.onLoad(p -> Log.v(TAG, "Load: " + p.url()));
            _page.onRequest(r -> _viewClient.onLoadResource(_view, r.url()));
            _page.onPageError(ex -> Log.w(TAG, "Page exception: " + ex));
            _page.onConsoleMessage(m -> Log.v(TAG, "console: " + m.text()));
            _page.route("**/*", route -> {
                WebResourceResponse resp = _viewClient.shouldInterceptRequest(_view, new PlaywrightWebResourceRequest(route));
                if (resp == null) {
                    route.resume();
                    return;
                }
                Log.v(TAG, "Intercepting request " + route.request().url());
                Map<String, String> headers = resp.getResponseHeaders();
                try {
                    headers.put("Content-Encoding", resp.getEncoding());
                } catch (Exception e) {
                    Log.v(TAG, "Failed to add content encoding", e);
                }
                byte[] data = null;
                try {
                    data = resp.getData().readAllBytes();
                } catch (Exception e) {
                    Log.v(TAG, "Failed read response body", e);
                }
                route.fulfill(
                    new Route.FulfillOptions()
                        .setStatus(resp.getStatusCode())
                        .setContentType(resp.getMimeType())
                        .setHeaders(headers)
                        .setBodyBytes(data)
                );
            });
        }
    }
}
