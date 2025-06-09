package xyz.nulldev.androidcompat.webkit

import android.annotation.SuppressLint
import android.annotation.SystemApi
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.print.PrintDocumentAdapter
import android.util.Log
import android.util.LongSparseArray
import android.util.SparseArray
import android.view.DragEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.textclassifier.TextClassifier
import android.webkit.*
import android.webkit.WebView.HitTestResult
import android.webkit.WebView.PictureListener
import android.webkit.WebView.VisualStateCallback
import android.webkit.WebViewProvider.ScrollDelegate
import android.webkit.WebViewProvider.ViewDelegate

import kotlin.collections.List
import kotlin.collections.Map

import java.io.BufferedWriter
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.Executor
import java.util.function.Consumer

class KcefWebViewProvider(view: WebView) : WebViewProvider {
    private val _view: WebView = view
    // val _settings: TODO: implement
    private var _viewClient: WebViewClient? = null // TODO: implement
    private var _chromeClient: WebChromeClient? = null // TODO: implement
    private val _mappings: MutableList<FunctionMapping> = mutableListOf()

    private val _handler: Handler = Handler(Looper.myLooper()!!)

    override fun init(javaScriptInterfaces: Map<String, Any>,
             privateBrowsing: Boolean) {
        destroy();
        // TODO: create
    }

    // Deprecated - should never be called
    override fun setHorizontalScrollbarOverlay(overlay: Boolean) {
        throw RuntimeException("Stub!");
    }

    // Deprecated - should never be called
    override fun setVerticalScrollbarOverlay(overlay: Boolean) {
        throw RuntimeException("Stub!");
    }

    // Deprecated - should never be called
    override fun overlayHorizontalScrollbar(): Boolean {
        throw RuntimeException("Stub!");
    }

    // Deprecated - should never be called
    override fun overlayVerticalScrollbar(): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun getVisibleTitleHeight(): Int {
        throw RuntimeException("Stub!");
    }

    override fun getCertificate(): SslCertificate {
        throw RuntimeException("Stub!");
    }

    override fun setCertificate(certificate: SslCertificate) {
        throw RuntimeException("Stub!");
    }

    override fun savePassword(host: String, username: String, password: String) {
        throw RuntimeException("Stub!");
    }

    override fun setHttpAuthUsernamePassword(host: String, realm: String,
            username: String, password: String) {
        throw RuntimeException("Stub!");
    }

    override fun getHttpAuthUsernamePassword(host: String, realm: String): Array<String> {
        throw RuntimeException("Stub!");
    }

    override fun destroy() {
        throw RuntimeException("Stub!");
    }

    override fun setNetworkAvailable(networkUp: Boolean) {
        throw RuntimeException("Stub!");
    }

    override fun saveState(outState: Bundle): WebBackForwardList {
        throw RuntimeException("Stub!");
    }

    override fun savePicture(b: Bundle, dest: File): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun restorePicture(b: Bundle, src: File): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun restoreState(inState: Bundle): WebBackForwardList {
        throw RuntimeException("Stub!");
    }

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        throw RuntimeException("Stub!");
    }

    override fun loadUrl(url: String) {
        loadUrl(url, mapOf());
    }

    override fun postUrl(url: String, postData: ByteArray) {
        throw RuntimeException("Stub!");
    }

    override fun loadData(data: String, mimeType: String, encoding: String) {
        loadDataWithBaseURL(null, data, mimeType, encoding, null);
    }

    override fun loadDataWithBaseURL(baseUrl: String?, data: String,
            mimeType: String, encoding: String, historyUrl: String?) {
        throw RuntimeException("Stub!");
    }

    override fun evaluateJavaScript(script: String, resultCallback: ValueCallback<String>) {
        throw RuntimeException("Stub!");
    }

    override fun saveWebArchive(filename: String) {
        throw RuntimeException("Stub!");
    }

    override fun saveWebArchive(basename: String, autoname: Boolean, callback: ValueCallback<String>) {
        throw RuntimeException("Stub!");
    }

    override fun stopLoading() {
        throw RuntimeException("Stub!");
    }

    override fun reload() {
        throw RuntimeException("Stub!");
    }

    override fun canGoBack(): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun goBack() {
        throw RuntimeException("Stub!");
    }

    override fun canGoForward(): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun goForward() {
        throw RuntimeException("Stub!");
    }

    override fun canGoBackOrForward(steps: Int): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun goBackOrForward(steps: Int) {
        throw RuntimeException("Stub!");
    }

    override fun isPrivateBrowsingEnabled(): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun pageUp(top: Boolean): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun pageDown(bottom: Boolean): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun insertVisualStateCallback(requestId: Long, callback: VisualStateCallback) {
        throw RuntimeException("Stub!");
    }

    override fun clearView() {
        throw RuntimeException("Stub!");
    }

    override fun capturePicture(): Picture {
        throw RuntimeException("Stub!");
    }

    override fun createPrintDocumentAdapter(documentName: String): PrintDocumentAdapter {
        throw RuntimeException("Stub!");
    }

    override fun getScale(): Float {
        throw RuntimeException("Stub!");
    }

    override fun setInitialScale(scaleInPercent: Int) {
        throw RuntimeException("Stub!");
    }

    override fun invokeZoomPicker() {
        throw RuntimeException("Stub!");
    }

    override fun getHitTestResult(): HitTestResult {
        throw RuntimeException("Stub!");
    }

    override fun requestFocusNodeHref(hrefMsg: Message) {
        throw RuntimeException("Stub!");
    }

    override fun requestImageRef(msg: Message) {
        throw RuntimeException("Stub!");
    }

    override fun getUrl(): String {
        throw RuntimeException("Stub!");
    }

    override fun getOriginalUrl(): String {
        throw RuntimeException("Stub!");
    }

    override fun getTitle(): String {
        throw RuntimeException("Stub!");
    }

    override fun getFavicon(): Bitmap {
        throw RuntimeException("Stub!");
    }

    override fun getTouchIconUrl(): String {
        throw RuntimeException("Stub!");
    }

    override fun getProgress(): Int {
        throw RuntimeException("Stub!");
    }

    override fun getContentHeight(): Int {
        throw RuntimeException("Stub!");
    }

    override fun getContentWidth(): Int {
        throw RuntimeException("Stub!");
    }

    override fun pauseTimers() {
        throw RuntimeException("Stub!");
    }

    override fun resumeTimers() {
        throw RuntimeException("Stub!");
    }

    override fun onPause() {
        throw RuntimeException("Stub!");
    }

    override fun onResume() {
        throw RuntimeException("Stub!");
    }

    override fun isPaused(): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun freeMemory() {
        throw RuntimeException("Stub!");
    }

    override fun clearCache(includeDiskFiles: Boolean) {
        throw RuntimeException("Stub!");
    }

    override fun clearFormData() {
        throw RuntimeException("Stub!");
    }

    override fun clearHistory() {
        throw RuntimeException("Stub!");
    }

    override fun clearSslPreferences() {
        throw RuntimeException("Stub!");
    }

    override fun copyBackForwardList(): WebBackForwardList {
        throw RuntimeException("Stub!");
    }

    override fun setFindListener(listener: WebView.FindListener) {
        throw RuntimeException("Stub!");
    }

    override fun findNext(forward: Boolean) {
        throw RuntimeException("Stub!");
    }

    override fun findAll(find: String): Int {
        throw RuntimeException("Stub!");
    }

    override fun findAllAsync(find: String) {
        throw RuntimeException("Stub!");
    }

    override fun showFindDialog(text: String, showIme: Boolean): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun clearMatches() {
        throw RuntimeException("Stub!");
    }

    override fun documentHasImages(response: Message) {
        throw RuntimeException("Stub!");
    }

    override fun setWebViewClient(client: WebViewClient) {
        _viewClient = client;
    }

    override fun getWebViewClient(): WebViewClient {
        return _viewClient!!;
    }

    override fun getWebViewRenderProcess(): WebViewRenderProcess? {
        throw RuntimeException("Stub!");
    }

    override fun setWebViewRenderProcessClient(
            executor: Executor?,
            client: WebViewRenderProcessClient?) {
        throw RuntimeException("Stub!");
    }

    override fun getWebViewRenderProcessClient(): WebViewRenderProcessClient? {
        throw RuntimeException("Stub!");
    }

    override fun setDownloadListener(listener: DownloadListener) {
        throw RuntimeException("Stub!");
    }

    override fun setWebChromeClient(client: WebChromeClient) {
        _chromeClient = client;
    }

    override fun getWebChromeClient(): WebChromeClient {
        return _chromeClient!!;
    }

    override fun setPictureListener(listener: PictureListener) {
        throw RuntimeException("Stub!");
    }

    private class FunctionMapping(val interfaceName: String, val functionName: String) {

        fun toExposed(): String {
            return "__\$_" + this.interfaceName + "|" + this.functionName;
        }

        fun toNice(): String {
            return this.interfaceName + "." + this.functionName;
        }
    }

    override fun addJavascriptInterface(obj: Any, interfaceName: String) {
        throw RuntimeException("Stub!");
    }

    override fun removeJavascriptInterface(interfaceName: String) {
        throw RuntimeException("Stub!");
    }

    override fun createWebMessageChannel(): Array<WebMessagePort> {
        throw RuntimeException("Stub!");
    }

    override fun postMessageToMainFrame(message: WebMessage, targetOrigin: Uri) {
        throw RuntimeException("Stub!");
    }

    override fun getSettings(): WebSettings {
        throw RuntimeException("Stub!");
        // return _settings;
    }

    override fun setMapTrackballToArrowKeys(setMap: Boolean) {
        throw RuntimeException("Stub!");
    }

    override fun flingScroll(vx: Int, vy: Int) {
        throw RuntimeException("Stub!");
    }

    override fun getZoomControls(): View {
        throw RuntimeException("Stub!");
    }

    override fun canZoomIn(): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun canZoomOut(): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun zoomBy(zoomFactor: Float): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun zoomIn(): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun zoomOut(): Boolean {
        throw RuntimeException("Stub!");
    }

    override fun dumpViewHierarchyWithProperties(out: BufferedWriter, level: Int) {
        throw RuntimeException("Stub!");
    }

    override fun findHierarchyView(className: String, hashCode: Int): View {
        throw RuntimeException("Stub!");
    }

    override fun setRendererPriorityPolicy(rendererRequestedPriority: Int, waivedWhenNotVisible: Boolean) {
        throw RuntimeException("Stub!");
    }

    override fun getRendererRequestedPriority(): Int {
        throw RuntimeException("Stub!");
    }

    override fun getRendererPriorityWaivedWhenNotVisible(): Boolean {
        throw RuntimeException("Stub!");
    }

    @SuppressWarnings("unused")
    override fun setTextClassifier(textClassifier: TextClassifier?) {}

    override fun getTextClassifier(): TextClassifier { return TextClassifier.NO_OP; }

    //-------------------------------------------------------------------------
    // Provider internal methods
    //-------------------------------------------------------------------------

    override fun getViewDelegate(): ViewDelegate {
        throw RuntimeException("Stub!");
    }

    override fun getScrollDelegate(): ScrollDelegate {
        throw RuntimeException("Stub!");
    }

    override fun notifyFindDialogDismissed() {
        throw RuntimeException("Stub!");
    }

    //-------------------------------------------------------------------------
    // View / ViewGroup delegation methods
    //-------------------------------------------------------------------------

    class KcefViewDelegate : ViewDelegate {
        override fun shouldDelayChildPressedState(): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onProvideVirtualStructure(structure: android.view.ViewStructure) {
            throw RuntimeException("Stub!");
        }

        override fun onProvideAutofillVirtualStructure(
                @SuppressWarnings("unused") structure: android.view.ViewStructure,
                @SuppressWarnings("unused") flags: Int) {
        }

        override fun autofill(@SuppressWarnings("unused") values: SparseArray<AutofillValue>) {
        }

        override fun isVisibleToUserForAutofill(@SuppressWarnings("unused") virtualId: Int): Boolean {
            return true; // true is the default value returned by View.isVisibleToUserForAutofill()
        }

        override fun onProvideContentCaptureStructure(
                @SuppressWarnings("unused") structure: android.view.ViewStructure,
                @SuppressWarnings("unused") flags: Int) {
        }

        override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider {
            throw RuntimeException("Stub!");
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            throw RuntimeException("Stub!");
        }

        override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
            throw RuntimeException("Stub!");
        }

        override fun performAccessibilityAction(action: Int, arguments: Bundle): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun setOverScrollMode(mode: Int) {
            throw RuntimeException("Stub!");
        }

        override fun setScrollBarStyle(style: Int) {
            throw RuntimeException("Stub!");
        }

        override fun onDrawVerticalScrollBar(canvas: Canvas, scrollBar: Drawable, l: Int, t: Int,
                r: Int, b: Int) {
            throw RuntimeException("Stub!");
        }

        override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
            throw RuntimeException("Stub!");
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            throw RuntimeException("Stub!");
        }

        override fun onDraw(canvas: Canvas) {
            throw RuntimeException("Stub!");
        }

        override fun setLayoutParams(layoutParams: LayoutParams) {
            throw RuntimeException("Stub!");
        }

        override fun performLongClick(): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            throw RuntimeException("Stub!");
        }

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
            throw RuntimeException("Stub!");
        }

        override fun onDragEvent(event: DragEvent): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onAttachedToWindow() {
            throw RuntimeException("Stub!");
        }

        override fun onDetachedFromWindow() {
            throw RuntimeException("Stub!");
        }

        override fun onMovedToDisplay(displayId: Int, config: Configuration) {}

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            throw RuntimeException("Stub!");
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            throw RuntimeException("Stub!");
        }

        override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect) {
            throw RuntimeException("Stub!");
        }

        override fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            throw RuntimeException("Stub!");
        }

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            throw RuntimeException("Stub!");
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onHoverEvent(event: MotionEvent): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onGenericMotionEvent(event: MotionEvent): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onTrackballEvent(ev: MotionEvent): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun requestFocus(direction: Int, previouslyFocusedRect: Rect): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            throw RuntimeException("Stub!");
        }

        override fun requestChildRectangleOnScreen(child: View, rect: Rect, immediate: Boolean): Boolean {
            throw RuntimeException("Stub!");
        }

        override fun setBackgroundColor(color: Int) {
            throw RuntimeException("Stub!");
        }

        override fun setLayerType(layerType: Int, paint: Paint ) {
            // ignore
        }

        override fun preDispatchDraw(canvas: Canvas) {
            throw RuntimeException("Stub!");
        }

        override fun onStartTemporaryDetach() {
            throw RuntimeException("Stub!");
        }

        override fun onFinishTemporaryDetach() {
            throw RuntimeException("Stub!");
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
            throw RuntimeException("Stub!");
        }

        override fun getHandler(originalHandler: Handler): Handler {
            throw RuntimeException("Stub!");
        }

        override fun findFocus(originalFocusedView: View): View {
            throw RuntimeException("Stub!");
        }

        @SuppressWarnings("unused")
        override fun onCheckIsTextEditor(): Boolean {
            return false;
        }

        @SuppressWarnings("unused")
        override fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets? {
            return null;
        }

        @SuppressWarnings("unused")
        override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
            return null;
        }
    }

    class PlaywrightScrollDelegate : ScrollDelegate {
        override fun computeHorizontalScrollRange(): Int {
            throw RuntimeException("Stub!");
        }

        override fun computeHorizontalScrollOffset(): Int {
            throw RuntimeException("Stub!");
        }

        override fun computeVerticalScrollRange(): Int {
            throw RuntimeException("Stub!");
        }

        override fun computeVerticalScrollOffset(): Int {
            throw RuntimeException("Stub!");
        }

        override fun computeVerticalScrollExtent(): Int {
            throw RuntimeException("Stub!");
        }

        override fun computeScroll() {
            throw RuntimeException("Stub!");
        }
    }
}
