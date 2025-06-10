package xyz.nulldev.androidcompat.webkit

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
import android.os.Message
import android.print.PrintDocumentAdapter
import android.util.Log
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
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import dev.datlag.kcef.KCEFResourceRequestHandler
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.collections.Map
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.callback.CefCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandler
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefPostData
import org.cef.network.CefPostDataElement
import org.cef.network.CefRequest
import org.cef.network.CefResponse

class KcefWebViewProvider(view: WebView) : WebViewProvider {
    private val view = view
    private val settings = KcefWebSettings()
    private var viewClient = WebViewClient()
    private var chromeClient = WebChromeClient()
    private val mappings: MutableList<FunctionMapping> = mutableListOf()

    private var kcefClient: KCEFClient? = null
    private var browser: KCEFBrowser? = null

    private val handler = Handler(view.webViewLooper)

    companion object {
        const val TAG = "KcefWebViewProvider"
        const val QUERY_FN = "__\$_suwayomiQuery"
        const val QUERY_CANCEL_FN = "__\$_suwayomiQueryCancel"
    }

    private class CefWebResourceRequest(
            val request: CefRequest?,
            val frame: CefFrame?,
            val redirect: Boolean
    ) : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(request?.url)

        override fun isForMainFrame(): Boolean = frame?.isMain ?: false

        override fun isRedirect(): Boolean = redirect

        override fun hasGesture(): Boolean = false

        override fun getMethod(): String = request?.method ?: "GET"

        override fun getRequestHeaders(): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            request?.getHeaderMap(headers)
            return headers
        }
    }

    private inner class DisplayHandler : CefDisplayHandlerAdapter() {
        override fun onConsoleMessage(
                browser: CefBrowser,
                level: CefSettings.LogSeverity,
                message: String,
                source: String,
                line: Int
        ): Boolean {
            Log.v(TAG, "$source:$line[$level]: $message")
            return true
        }

        override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
            Log.d(TAG, "Navigate to $url")
        }

        override fun onStatusMessage(browser: CefBrowser, value: String) {
            Log.v(TAG, "Status update: $value")
        }
    }

    private inner class LoadHandler : CefLoadHandlerAdapter() {
        override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
            val url = frame.url ?: ""
            Log.v(TAG, "Load end $url")
            handler.post {
                if (httpStatusCode == 404) {
                    viewClient.onReceivedError(
                            view,
                            WebViewClient.ERROR_FILE_NOT_FOUND,
                            "Not Found",
                            url
                    )
                }
                if (httpStatusCode == 429) {
                    viewClient.onReceivedError(
                            view,
                            WebViewClient.ERROR_TOO_MANY_REQUESTS,
                            "Too Many Requests",
                            url
                    )
                }
                if (httpStatusCode >= 400) {
                    // TODO: create request and response
                    // viewClient.onReceivedHttpError(_view, ...);
                }
                viewClient.onPageFinished(view, url)
                chromeClient.onProgressChanged(view, 100)
            }
        }

        override fun onLoadError(
                browser: CefBrowser,
                frame: CefFrame,
                errorCode: CefLoadHandler.ErrorCode,
                errorText: String,
                failedUrl: String
        ) {
            Log.w(TAG, "Load error ($failedUrl) [$errorCode]: $errorText")
            // TODO: translate correctly
            handler.post {
                viewClient.onReceivedError(view, WebViewClient.ERROR_UNKNOWN, errorText, url)
            }
        }

        override fun onLoadStart(
                browser: CefBrowser,
                frame: CefFrame,
                transitionType: CefRequest.TransitionType
        ) {
            Log.v(TAG, "Load start, pushing mappings")
            mappings.forEach {
                val js =
                        """
                        window.${it.interfaceName} = window.${it.interfaceName} || {}
                        window.${it.interfaceName}.${it.functionName} = function() {
                            return new Promise((resolve, reject) => {
                                window.${QUERY_FN}({
                                    request: JSON.stringify({
                                        functionName: ${Json.encodeToString(it.functionName)},
                                        interfaceName: ${Json.encodeToString(it.interfaceName)},
                                        args: Array.from(arguments),
                                    }),
                                    persistent: false,
                                    onSuccess: resolve,
                                    onFailure: (_, err) => reject(err),
                                })
                            });
                        }
                        """
                browser.executeJavaScript(js, "SUWAYOMI ${it.toNice()}", 0)
            }

            handler.post { viewClient.onPageStarted(view, frame.url, null) }
        }
    }

    private inner class MessageRouterHandler : CefMessageRouterHandlerAdapter() {
        override fun onQuery(
                browser: CefBrowser,
                frame: CefFrame,
                queryId: Long,
                request: String,
                persistent: Boolean,
                callback: CefQueryCallback
        ): Boolean {
            val invoke =
                    try {
                        Json.decodeFromString<FunctionCall>(request)
                    } catch (e: Exception) {
                        Log.w(TAG, "Invalid request received $e")
                        return false
                    }
            // TODO: Use a map
            mappings
                    .find {
                        it.functionName == invoke.functionName &&
                                it.interfaceName == invoke.interfaceName
                    }
                    ?.let {
                        handler.post {
                            try {
                                Log.v(TAG, "Received request to invoke ${it.toNice()}")
                                // NOTE: first argument is
                                // implicitly this
                                val retval = it.fn.call(it.obj, *invoke.args)
                                callback.success(retval.toString())
                            } catch (e: Exception) {
                                Log.w(TAG, "JS-invoke on ${it.toNice()} failed: $e")
                                callback.failure(0, e.message)
                            }
                        }
                        return true
                    }
            return false
        }
    }

    private inner class WebResponseResourceHandler(val webResponse: WebResourceResponse) :
            CefResourceHandlerAdapter() {
        private var resolvedData: ByteArray? = null
        private var readOffset = 0

        override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
            Log.v(TAG, "Handling request from client's response for ${request.url}")
            handler.post {
                try {
                    resolvedData = webResponse.data.readAllBytes()
                } catch (e: IOException) {}
                callback.Continue()
            }
            return true
        }

        override fun getResponseHeaders(
                response: CefResponse,
                responseLength: IntRef,
                redirectUrl: StringRef
        ) {
            webResponse.responseHeaders.forEach { response.setHeaderByName(it.key, it.value, true) }
            responseLength.set(resolvedData?.size ?: 0)
        }

        override fun readResponse(
                dataOut: ByteArray,
                bytesToRead: Int,
                bytesRead: IntRef,
                callback: CefCallback
        ): Boolean {
            val data = resolvedData ?: return false
            var bytesToTransfer = Math.min(bytesToRead, data.size - readOffset)
            Log.v(
                    TAG,
                    "readResponse: $readOffset/${data.size}, reading $bytesToRead->$bytesToTransfer"
            )
            data.copyInto(dataOut, startIndex = readOffset, endIndex = readOffset + bytesToTransfer)
            bytesRead.set(bytesToTransfer)
            readOffset += bytesToTransfer
            return readOffset < data.size
        }
    }

    private inner class ResourceRequestHandler : CefResourceRequestHandlerAdapter() {
        override fun onBeforeResourceLoad(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest
        ): Boolean {
            request.setHeaderByName("user-agent", settings.userAgentString, true)

            // TODO: we should be calling this on the handler, since CEF calls us on its IO thread
            // thus if a client tried to use WebView#loadUrl as the docs suggest, this fails
            val cancel =
                    viewClient.shouldOverrideUrlLoading(
                            view,
                            CefWebResourceRequest(request, frame, false),
                    )
            Log.v(TAG, "Resource ${request?.url}, result is cancel? $cancel")

            handler.post { viewClient.onLoadResource(view, frame?.url) }

            return cancel || settings.blockNetworkLoads
        }

        override fun getResourceHandler(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest
        ): CefResourceHandler? {
            // TODO: we should be calling this on the handler, since CEF calls us on its IO thread
            val response =
                    viewClient.shouldInterceptRequest(
                            view,
                            CefWebResourceRequest(request, frame, false),
                    )
            response ?: return null
            return WebResponseResourceHandler(response)
        }
    }

    private inner class RequestHandler : CefRequestHandlerAdapter() {
        override fun getResourceRequestHandler(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest,
                isNavigation: Boolean,
                isDownload: Boolean,
                requestInitiator: String,
                disableDefaultHandling: BoolRef
        ): CefResourceRequestHandler? = ResourceRequestHandler()

        override fun onRenderProcessTerminated(
                browser: CefBrowser,
                status: CefRequestHandler.TerminationStatus
        ) {
            handler.post {
                viewClient.onRenderProcessGone(
                        view,
                        object : RenderProcessGoneDetail() {
                            override fun didCrash(): Boolean =
                                    status == CefRequestHandler.TerminationStatus.TS_PROCESS_CRASHED
                            override fun rendererPriorityAtExit(): Int = -1
                        }
                )
            }
        }
    }

    override fun init(javaScriptInterfaces: Map<String, Any>?, privateBrowsing: Boolean) {
        destroy()
        kcefClient =
                KCEF.newClientBlocking().apply {
                    addDisplayHandler(DisplayHandler())
                    addLoadHandler(LoadHandler())
                    addRequestHandler(RequestHandler())

                    var config = CefMessageRouter.CefMessageRouterConfig()
                    config.jsQueryFunction = QUERY_FN
                    config.jsCancelFunction = QUERY_CANCEL_FN
                    addMessageRouter(CefMessageRouter.create(config, MessageRouterHandler()))
                }
    }

    // Deprecated - should never be called
    override fun setHorizontalScrollbarOverlay(overlay: Boolean) {
        throw RuntimeException("Stub!")
    }

    // Deprecated - should never be called
    override fun setVerticalScrollbarOverlay(overlay: Boolean) {
        throw RuntimeException("Stub!")
    }

    // Deprecated - should never be called
    override fun overlayHorizontalScrollbar(): Boolean {
        throw RuntimeException("Stub!")
    }

    // Deprecated - should never be called
    override fun overlayVerticalScrollbar(): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun getVisibleTitleHeight(): Int {
        throw RuntimeException("Stub!")
    }

    override fun getCertificate(): SslCertificate {
        throw RuntimeException("Stub!")
    }

    override fun setCertificate(certificate: SslCertificate) {
        throw RuntimeException("Stub!")
    }

    override fun savePassword(host: String, username: String, password: String) {
        throw RuntimeException("Stub!")
    }

    override fun setHttpAuthUsernamePassword(
            host: String,
            realm: String,
            username: String,
            password: String
    ) {
        throw RuntimeException("Stub!")
    }

    override fun getHttpAuthUsernamePassword(host: String, realm: String): Array<String> {
        throw RuntimeException("Stub!")
    }

    override fun destroy() {
        browser?.close(true)
        browser?.dispose()
        browser = null
        kcefClient?.dispose()
        kcefClient = null
    }

    override fun setNetworkAvailable(networkUp: Boolean) {
        throw RuntimeException("Stub!")
    }

    override fun saveState(outState: Bundle): WebBackForwardList {
        throw RuntimeException("Stub!")
    }

    override fun savePicture(b: Bundle, dest: File): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun restorePicture(b: Bundle, src: File): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun restoreState(inState: Bundle): WebBackForwardList {
        throw RuntimeException("Stub!")
    }

    override fun loadUrl(loadUrl: String, additionalHttpHeaders: Map<String, String>) {
        browser?.close(true)
        browser?.dispose()
        chromeClient.onProgressChanged(view, 0)
        browser =
                kcefClient!!.createBrowser(
                                loadUrl,
                                CefRendering.OFFSCREEN,
                                context = createContext(additionalHttpHeaders)
                        )
                        .apply {
                            // NOTE: Without this, we don't seem to be receiving any events
                            createImmediately()
                        }
        Log.d(TAG, "Page loaded at URL $loadUrl")
    }

    override fun loadUrl(url: String) {
        loadUrl(url, mapOf())
    }

    override fun postUrl(url: String, postData: ByteArray) {
        browser?.close(true)
        browser?.dispose()
        chromeClient.onProgressChanged(view, 0)
        browser =
                kcefClient!!.createBrowser(
                                url,
                                CefRendering.OFFSCREEN,
                                context = createContext(postData = postData)
                        )
                        .apply {
                            // NOTE: Without this, we don't seem to be receiving any events
                            createImmediately()
                        }
        Log.d(TAG, "Page posted at URL $url")
    }

    override fun loadData(data: String, mimeType: String, encoding: String) {
        loadDataWithBaseURL(null, data, mimeType, encoding, null)
    }

    override fun loadDataWithBaseURL(
            baseUrl: String?,
            data: String,
            mimeType: String,
            encoding: String,
            historyUrl: String?
    ) {
        browser?.close(true)
        browser?.dispose()
        chromeClient.onProgressChanged(view, 0)
        browser =
                kcefClient!!.createBrowserWithHtml(
                                data,
                                baseUrl ?: KCEFBrowser.BLANK_URI,
                                CefRendering.OFFSCREEN
                        )
                        .apply {
                            // NOTE: Without this, we don't seem to be receiving any events
                            createImmediately()
                        }
        Log.d(TAG, "Page loaded from data at base URL $baseUrl")
    }

    override fun evaluateJavaScript(script: String, resultCallback: ValueCallback<String>) {
        browser!!.evaluateJavaScript(
                script.removePrefix("javascript:"),
                {
                    Log.v(TAG, "JS returned: $it")
                    it?.let { resultCallback.onReceiveValue(it) }
                }
        )
    }

    override fun saveWebArchive(filename: String) {
        throw RuntimeException("Stub!")
    }

    override fun saveWebArchive(
            basename: String,
            autoname: Boolean,
            callback: ValueCallback<String>
    ) {
        throw RuntimeException("Stub!")
    }

    override fun stopLoading() {
        browser!!.stopLoad()
    }

    override fun reload() {
        browser!!.reload()
    }

    override fun canGoBack(): Boolean = browser!!.canGoBack()

    override fun goBack() {
        browser!!.goBack()
    }

    override fun canGoForward(): Boolean = browser!!.canGoForward()

    override fun goForward() {
        browser!!.goForward()
    }

    override fun canGoBackOrForward(steps: Int): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun goBackOrForward(steps: Int) {
        throw RuntimeException("Stub!")
    }

    override fun isPrivateBrowsingEnabled(): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun pageUp(top: Boolean): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun pageDown(bottom: Boolean): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun insertVisualStateCallback(requestId: Long, callback: VisualStateCallback) {
        throw RuntimeException("Stub!")
    }

    override fun clearView() {
        throw RuntimeException("Stub!")
    }

    override fun capturePicture(): Picture {
        throw RuntimeException("Stub!")
    }

    override fun createPrintDocumentAdapter(documentName: String): PrintDocumentAdapter {
        throw RuntimeException("Stub!")
    }

    override fun getScale(): Float {
        throw RuntimeException("Stub!")
    }

    override fun setInitialScale(scaleInPercent: Int) {
        throw RuntimeException("Stub!")
    }

    override fun invokeZoomPicker() {
        throw RuntimeException("Stub!")
    }

    override fun getHitTestResult(): HitTestResult {
        throw RuntimeException("Stub!")
    }

    override fun requestFocusNodeHref(hrefMsg: Message) {
        throw RuntimeException("Stub!")
    }

    override fun requestImageRef(msg: Message) {
        throw RuntimeException("Stub!")
    }

    override fun getUrl(): String = browser!!.url

    override fun getOriginalUrl(): String = browser!!.url

    override fun getTitle(): String {
        throw RuntimeException("Stub!")
    }

    override fun getFavicon(): Bitmap {
        throw RuntimeException("Stub!")
    }

    override fun getTouchIconUrl(): String {
        throw RuntimeException("Stub!")
    }

    override fun getProgress(): Int {
        throw RuntimeException("Stub!")
    }

    override fun getContentHeight(): Int {
        throw RuntimeException("Stub!")
    }

    override fun getContentWidth(): Int {
        throw RuntimeException("Stub!")
    }

    override fun pauseTimers() {
        throw RuntimeException("Stub!")
    }

    override fun resumeTimers() {
        throw RuntimeException("Stub!")
    }

    override fun onPause() {
        throw RuntimeException("Stub!")
    }

    override fun onResume() {
        throw RuntimeException("Stub!")
    }

    override fun isPaused(): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun freeMemory() {
        throw RuntimeException("Stub!")
    }

    override fun clearCache(includeDiskFiles: Boolean) {
        throw RuntimeException("Stub!")
    }

    override fun clearFormData() {
        throw RuntimeException("Stub!")
    }

    override fun clearHistory() {
        throw RuntimeException("Stub!")
    }

    override fun clearSslPreferences() {
        throw RuntimeException("Stub!")
    }

    override fun copyBackForwardList(): WebBackForwardList {
        throw RuntimeException("Stub!")
    }

    override fun setFindListener(listener: WebView.FindListener) {
        throw RuntimeException("Stub!")
    }

    override fun findNext(forward: Boolean) {
        throw RuntimeException("Stub!")
    }

    override fun findAll(find: String): Int {
        throw RuntimeException("Stub!")
    }

    override fun findAllAsync(find: String) {
        throw RuntimeException("Stub!")
    }

    override fun showFindDialog(text: String, showIme: Boolean): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun clearMatches() {
        throw RuntimeException("Stub!")
    }

    override fun documentHasImages(response: Message) {
        throw RuntimeException("Stub!")
    }

    override fun setWebViewClient(client: WebViewClient) {
        viewClient = client
    }

    override fun getWebViewClient(): WebViewClient = viewClient

    override fun getWebViewRenderProcess(): WebViewRenderProcess? {
        throw RuntimeException("Stub!")
    }

    override fun setWebViewRenderProcessClient(
            executor: Executor?,
            client: WebViewRenderProcessClient?
    ) {
        throw RuntimeException("Stub!")
    }

    override fun getWebViewRenderProcessClient(): WebViewRenderProcessClient? {
        throw RuntimeException("Stub!")
    }

    override fun setDownloadListener(listener: DownloadListener) {
        throw RuntimeException("Stub!")
    }

    override fun setWebChromeClient(client: WebChromeClient) {
        chromeClient = client
    }

    override fun getWebChromeClient(): WebChromeClient = chromeClient

    override fun setPictureListener(listener: PictureListener) {
        throw RuntimeException("Stub!")
    }

    @Serializable
    private data class FunctionCall(
            val interfaceName: String,
            val functionName: String,
            val args: Array<String>
    )

    private data class FunctionMapping(
            val interfaceName: String,
            val functionName: String,
            val obj: Any,
            val fn: KFunction<*>
    ) {
        fun toNice(): String = "$interfaceName.$functionName"
    }

    override fun addJavascriptInterface(obj: Any, interfaceName: String) {
        val cls = obj::class
        mappings.addAll(
                cls.declaredMemberFunctions.map {
                    val map = FunctionMapping(interfaceName, it.name, obj, it)
                    Log.v(TAG, "Exposing: " + map.toNice())
                    map
                }
        )
    }

    override fun removeJavascriptInterface(interfaceName: String) {
        throw RuntimeException("Stub!")
    }

    override fun createWebMessageChannel(): Array<WebMessagePort> {
        throw RuntimeException("Stub!")
    }

    override fun postMessageToMainFrame(message: WebMessage, targetOrigin: Uri) {
        throw RuntimeException("Stub!")
    }

    override fun getSettings(): WebSettings = settings

    override fun setMapTrackballToArrowKeys(setMap: Boolean) {
        throw RuntimeException("Stub!")
    }

    override fun flingScroll(vx: Int, vy: Int) {
        throw RuntimeException("Stub!")
    }

    override fun getZoomControls(): View {
        throw RuntimeException("Stub!")
    }

    override fun canZoomIn(): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun canZoomOut(): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun zoomBy(zoomFactor: Float): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun zoomIn(): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun zoomOut(): Boolean {
        throw RuntimeException("Stub!")
    }

    override fun dumpViewHierarchyWithProperties(out: BufferedWriter, level: Int) {
        throw RuntimeException("Stub!")
    }

    override fun findHierarchyView(className: String, hashCode: Int): View {
        throw RuntimeException("Stub!")
    }

    override fun setRendererPriorityPolicy(
            rendererRequestedPriority: Int,
            waivedWhenNotVisible: Boolean
    ) {
        throw RuntimeException("Stub!")
    }

    override fun getRendererRequestedPriority(): Int {
        throw RuntimeException("Stub!")
    }

    override fun getRendererPriorityWaivedWhenNotVisible(): Boolean {
        throw RuntimeException("Stub!")
    }

    @SuppressWarnings("unused") override fun setTextClassifier(textClassifier: TextClassifier?) {}

    override fun getTextClassifier(): TextClassifier = TextClassifier.NO_OP

    // -------------------------------------------------------------------------
    // Provider internal methods
    // -------------------------------------------------------------------------

    override fun getViewDelegate(): ViewDelegate {
        throw RuntimeException("Stub!")
    }

    override fun getScrollDelegate(): ScrollDelegate {
        throw RuntimeException("Stub!")
    }

    override fun notifyFindDialogDismissed() {
        throw RuntimeException("Stub!")
    }

    // -------------------------------------------------------------------------
    // View / ViewGroup delegation methods
    // -------------------------------------------------------------------------

    class KcefViewDelegate : ViewDelegate {
        override fun shouldDelayChildPressedState(): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onProvideVirtualStructure(structure: android.view.ViewStructure) {
            throw RuntimeException("Stub!")
        }

        override fun onProvideAutofillVirtualStructure(
                @SuppressWarnings("unused") structure: android.view.ViewStructure,
                @SuppressWarnings("unused") flags: Int
        ) {}

        override fun autofill(@SuppressWarnings("unused") values: SparseArray<AutofillValue>) {}

        override fun isVisibleToUserForAutofill(
                @SuppressWarnings("unused") virtualId: Int
        ): Boolean {
            return true // true is the default value returned by View.isVisibleToUserForAutofill()
        }

        override fun onProvideContentCaptureStructure(
                @SuppressWarnings("unused") structure: android.view.ViewStructure,
                @SuppressWarnings("unused") flags: Int
        ) {}

        override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider {
            throw RuntimeException("Stub!")
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            throw RuntimeException("Stub!")
        }

        override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
            throw RuntimeException("Stub!")
        }

        override fun performAccessibilityAction(action: Int, arguments: Bundle): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun setOverScrollMode(mode: Int) {
            throw RuntimeException("Stub!")
        }

        override fun setScrollBarStyle(style: Int) {
            throw RuntimeException("Stub!")
        }

        override fun onDrawVerticalScrollBar(
                canvas: Canvas,
                scrollBar: Drawable,
                l: Int,
                t: Int,
                r: Int,
                b: Int
        ) {
            throw RuntimeException("Stub!")
        }

        override fun onOverScrolled(
                scrollX: Int,
                scrollY: Int,
                clampedX: Boolean,
                clampedY: Boolean
        ) {
            throw RuntimeException("Stub!")
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            throw RuntimeException("Stub!")
        }

        override fun onDraw(canvas: Canvas) {
            throw RuntimeException("Stub!")
        }

        override fun setLayoutParams(layoutParams: LayoutParams) {
            throw RuntimeException("Stub!")
        }

        override fun performLongClick(): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            throw RuntimeException("Stub!")
        }

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
            throw RuntimeException("Stub!")
        }

        override fun onDragEvent(event: DragEvent): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onAttachedToWindow() {
            throw RuntimeException("Stub!")
        }

        override fun onDetachedFromWindow() {
            throw RuntimeException("Stub!")
        }

        override fun onMovedToDisplay(displayId: Int, config: Configuration) {}

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            throw RuntimeException("Stub!")
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            throw RuntimeException("Stub!")
        }

        override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect) {
            throw RuntimeException("Stub!")
        }

        override fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            throw RuntimeException("Stub!")
        }

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            throw RuntimeException("Stub!")
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onHoverEvent(event: MotionEvent): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onGenericMotionEvent(event: MotionEvent): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onTrackballEvent(ev: MotionEvent): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun requestFocus(direction: Int, previouslyFocusedRect: Rect): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            throw RuntimeException("Stub!")
        }

        override fun requestChildRectangleOnScreen(
                child: View,
                rect: Rect,
                immediate: Boolean
        ): Boolean {
            throw RuntimeException("Stub!")
        }

        override fun setBackgroundColor(color: Int) {
            throw RuntimeException("Stub!")
        }

        override fun setLayerType(layerType: Int, paint: Paint) {
            // ignore
        }

        override fun preDispatchDraw(canvas: Canvas) {
            throw RuntimeException("Stub!")
        }

        override fun onStartTemporaryDetach() {
            throw RuntimeException("Stub!")
        }

        override fun onFinishTemporaryDetach() {
            throw RuntimeException("Stub!")
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
            throw RuntimeException("Stub!")
        }

        override fun getHandler(originalHandler: Handler): Handler {
            throw RuntimeException("Stub!")
        }

        override fun findFocus(originalFocusedView: View): View {
            throw RuntimeException("Stub!")
        }

        @SuppressWarnings("unused") override fun onCheckIsTextEditor(): Boolean = false

        @SuppressWarnings("unused")
        override fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets? = null

        @SuppressWarnings("unused")
        override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? =
                null
    }

    class KcefScrollDelegate : ScrollDelegate {
        override fun computeHorizontalScrollRange(): Int {
            throw RuntimeException("Stub!")
        }

        override fun computeHorizontalScrollOffset(): Int {
            throw RuntimeException("Stub!")
        }

        override fun computeVerticalScrollRange(): Int {
            throw RuntimeException("Stub!")
        }

        override fun computeVerticalScrollOffset(): Int {
            throw RuntimeException("Stub!")
        }

        override fun computeVerticalScrollExtent(): Int {
            throw RuntimeException("Stub!")
        }

        override fun computeScroll() {
            throw RuntimeException("Stub!")
        }
    }

    private fun createContext(
            additionalHttpHeaders: Map<String, String>? = null,
            postData: ByteArray? = null,
    ): CefRequestContext =
            CefRequestContext.createContext {
                    browser,
                    frame,
                    request,
                    isNavigation,
                    isDownload,
                    requestInitiator,
                    disableDefaultHandling ->
                KCEFResourceRequestHandler.globalHandler(
                        browser,
                        frame,
                        request.apply {
                            if (!additionalHttpHeaders.isNullOrEmpty()) {
                                additionalHttpHeaders.forEach {
                                    setHeaderByName(it.key, it.value, true)
                                }
                            }

                            if (postData != null) {
                                this.postData =
                                        CefPostData.create().apply {
                                            addElement(
                                                    CefPostDataElement.create().apply {
                                                        setToBytes(postData.size, postData)
                                                    },
                                            )
                                        }
                            }
                        },
                        isNavigation,
                        isDownload,
                        requestInitiator,
                        disableDefaultHandling,
                )
            }
}
