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
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import android.webkit.WebView.PictureListener
import android.webkit.WebView.VisualStateCallback
import android.webkit.WebViewClient
import android.webkit.WebViewProvider
import android.webkit.WebViewProvider.ScrollDelegate
import android.webkit.WebViewProvider.ViewDelegate
import android.webkit.WebViewRenderProcess
import android.webkit.WebViewRenderProcessClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefCallback
import org.cef.callback.CefMediaAccessCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefPermissionHandler
import org.cef.handler.CefRenderHandlerAdapter
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
import org.koin.mp.KoinPlatformTools
import java.awt.Rectangle
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import javax.swing.JPanel
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

class KcefWebViewProvider(
    private val view: WebView,
) : WebViewProvider {
    private val settings = KcefWebSettings()
    private var viewClient = WebViewClient()
    private var chromeClient = WebChromeClient()
    private val renderHandler = RenderHandler()

    // CopyOnWriteArrayList: iterated from CEF IO threads while mutated from
    // the WebView thread.
    private val mappings: MutableList<FunctionMapping> = CopyOnWriteArrayList()

    /**
     * HTML payloads registered via loadData-like and loadUrl-style calls, keyed by the
     * normalized request URL. Cleared on every navigation so stale entries do
     * not leak across pages.
     */
    private val urlHttpMapping: MutableMap<String, CachedPage> = ConcurrentHashMap()
    private var initialRequestData: InitialRequestData? = null

    private var kcefClient: CefClient? = null
    private var browser: CefBrowser? = null

    /** Cached page title (CEF pushes updates via the display handler). */
    @Volatile
    private var lastTitle: String = ""

    @Volatile
    private var lastProgress: Int = 0

    @Volatile
    private var privateBrowsing: Boolean = false

    /**
     * Navigation requested before the CEF client finished initializing;
     * replayed once init completes.
     */
    @Volatile
    private var pendingAction: (() -> Unit)? = null

    private val handler = Handler(view.webViewLooper)

    companion object {
        const val TAG = "KcefWebViewProvider"
        const val QUERY_FN = "__\$_suwayomiQuery"
        const val QUERY_CANCEL_FN = "__\$_suwayomiQueryCancel"

        private val json = Json { ignoreUnknownKeys = true }

        private const val DEFAULT_VIEWPORT_WIDTH = 1280
        private const val DEFAULT_VIEWPORT_HEIGHT = 2856

        private val initHandler: InitBrowserHandler by KoinPlatformTools.defaultContext().get().inject()

        /**
         * Strip query string and fragment (which may carry tokens/credentials)
         * from a URL before logging it.
         */
        internal fun String.sanitizedForLog(): String = substringBefore('?').substringBefore('#')
    }

    interface InitBrowserHandler {
        fun init(provider: KcefWebViewProvider): Unit
    }

    private inner class LifeSpanHandler : CefLifeSpanHandlerAdapter() {
        override fun onAfterCreated(browser: CefBrowser) {
            val ua = settings.userAgentString
            val args =
                settings.getUserAgentMetadata()?.let {
                    Log.d(TAG, "Using user-agent $ua with metadata $it")
                    """
                {
                    "userAgent": ${Json.encodeToString(ua)},
                    "userAgentMetadata": ${Json.encodeToString(it)}
                }
                """
                } ?: run {
                    Log.d(TAG, "Using user-agent $ua without metadata")
                    """
                {
                    "userAgent": ${Json.encodeToString(ua)}
                }
                """
                }
            browser.devToolsClient.executeDevToolsMethod("Emulation.setUserAgentOverride", args).whenComplete { res, ex ->
                if (ex != null) {
                    Log.e(TAG, "Failed to set user agent", ex)
                } else {
                    Log.v(TAG, "Successfully set user agent: $res")
                }
            }
        }
    }

    private data class InitialRequestData(
        private val additionalHttpHeaders: Map<String, String>? = null,
        private val myPostData: ByteArray? = null,
    ) {
        fun apply(request: CefRequest?) {
            request?.apply {
                Log.v(TAG, "Initial request: applying headers and post data")
                if (!additionalHttpHeaders.isNullOrEmpty()) {
                    additionalHttpHeaders.forEach {
                        setHeaderByName(it.key, it.value, true)
                    }
                }

                if (myPostData != null) {
                    this.postData =
                        CefPostData.create().apply {
                            addElement(
                                CefPostDataElement.create().apply {
                                    setToBytes(myPostData.size, myPostData)
                                },
                            )
                        }
                }
            }
        }
    }

    private class CefWebResourceRequest(
        val request: CefRequest?,
        val frame: CefFrame?,
        val redirect: Boolean,
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

    private class CefPermissionRequest(
        private val url: String,
        private val permissionMask: Int,
        private val callback: CefMediaAccessCallback,
    ) : PermissionRequest() {
        override fun getOrigin(): Uri = Uri.parse(url)

        override fun getResources(): Array<String> {
            val retVal = mutableListOf<String>()
            if ((permissionMask and (1 shl 0)) > 0) retVal.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            if ((permissionMask and (1 shl 1)) > 0) retVal.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            return retVal.toTypedArray()
        }

        override fun grant(resources: Array<String>) {
            // Respect the caller's resource selection: only grant the CEF
            // permission bits that correspond to the requested resources.
            var grantedMask = 0
            resources.forEach {
                when (it) {
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> grantedMask = grantedMask or (1 shl 0)
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> grantedMask = grantedMask or (1 shl 1)
                }
            }
            callback.Continue(grantedMask and permissionMask)
        }

        override fun deny() {
            callback.Cancel()
        }
    }

    private inner class DisplayHandler : CefDisplayHandlerAdapter() {
        override fun onConsoleMessage(
            browser: CefBrowser,
            level: CefSettings.LogSeverity,
            message: String,
            source: String,
            line: Int,
        ): Boolean {
            Log.v(TAG, "$source:$line[$level]: $message")
            return true
        }

        override fun onTitleChange(
            browser: CefBrowser,
            title: String,
        ) {
            lastTitle = title
        }

        override fun onAddressChange(
            browser: CefBrowser,
            frame: CefFrame,
            url: String,
        ) {
            Log.d(TAG, "Navigate to ${url.sanitizedForLog()}")
        }

        override fun onStatusMessage(
            browser: CefBrowser,
            value: String,
        ) {
            Log.v(TAG, "Status update: $value")
        }
    }

    @Suppress("DEPRECATION")
    private inner class LoadHandler : CefLoadHandlerAdapter() {
        override fun onLoadEnd(
            browser: CefBrowser,
            frame: CefFrame,
            httpStatusCode: Int,
        ) {
            val url = frame.url ?: ""
            // WebView client callbacks only cover the main frame.
            if (!frame.isMain) {
                return
            }
            Log.v(TAG, "Load end ${url.sanitizedForLog()}")
            handler.post {
                if (httpStatusCode == 404) {
                    viewClient.onReceivedError(
                        view,
                        WebViewClient.ERROR_FILE_NOT_FOUND,
                        "Not Found",
                        url,
                    )
                }
                if (httpStatusCode == 429) {
                    viewClient.onReceivedError(
                        view,
                        WebViewClient.ERROR_TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        url,
                    )
                }
                if (httpStatusCode >= 400) {
                    // TODO: create request and response
                    // viewClient.onReceivedHttpError(_view, ...);
                }
                lastProgress = 100
                viewClient.onPageFinished(view, url)
                chromeClient.onProgressChanged(view, 100)
            }
        }

        override fun onLoadError(
            browser: CefBrowser,
            frame: CefFrame,
            errorCode: CefLoadHandler.ErrorCode,
            errorText: String,
            failedUrl: String,
        ) {
            Log.w(TAG, "Load error (${failedUrl.sanitizedForLog()}) [$errorCode]: $errorText")
            // WebView client callbacks only cover the main frame.
            if (!frame.isMain) {
                return
            }
            // TODO: translate correctly
            handler.post {
                viewClient.onReceivedError(view, WebViewClient.ERROR_UNKNOWN, errorText, frame.url)
            }
        }

        override fun onLoadStart(
            browser: CefBrowser,
            frame: CefFrame,
            transitionType: CefRequest.TransitionType,
        ) {
            Log.v(TAG, "Load start, pushing mappings")
            mappings.forEach {
                val js =
                    """
                        window.${it.interfaceName} = window.${it.interfaceName} || {}
                        window.${it.interfaceName}.${it.functionName} = async function() {
                            const args = await Promise.all(Array.from(arguments));
                            return new Promise((resolve, reject) => {
                                window.${QUERY_FN}({
                                    request: JSON.stringify({
                                        functionName: ${Json.encodeToString(it.functionName)},
                                        interfaceName: ${Json.encodeToString(it.interfaceName)},
                                        args,
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

            // WebView client callbacks only cover the main frame.
            if (!frame.isMain) {
                return
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
            callback: CefQueryCallback,
        ): Boolean {
            val invoke =
                try {
                    json.decodeFromString<FunctionCall>(request)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid request received $e")
                    return false
                }
            // TODO: Use a map
            mappings
                .find {
                    it.functionName == invoke.functionName &&
                        it.interfaceName == invoke.interfaceName
                }?.let {
                    handler.post {
                        try {
                            Log.v(
                                TAG,
                                "Received request to invoke ${it.toNice()} with ${invoke.args.size} args",
                            )
                            // NOTE: first argument is
                            // implicitly this
                            val kotlinArgs = invoke.toKotlinArgs(it.fn)
                            val retval = it.fn.call(it.obj, *kotlinArgs)
                            callback.success(retval.toString())
                        } catch (e: Exception) {
                            Log.w(TAG, "JS-invoke on ${it.toNice()} failed:", e)
                            callback.failure(0, e.message)
                        }
                    }
                    return true
                }
            return false
        }
    }

    private abstract class ArrayResponseResourceHandler : CefResourceHandlerAdapter() {
        protected var resolvedData: ByteArray? = null
        protected var readOffset = 0

        override fun getResponseHeaders(
            response: CefResponse,
            responseLength: IntRef,
            redirectUrl: StringRef,
        ) {
            responseLength.set(resolvedData?.size ?: 0)
            response.status = 200
            response.statusText = "OK"
            response.mimeType = "text/html"
        }

        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean {
            val data = resolvedData ?: return false
            val bytesToTransfer = min(bytesToRead, data.size - readOffset)
            Log.v(
                TAG,
                "readResponse: $readOffset/${data.size}, reading $bytesToRead->$bytesToTransfer",
            )
            data.copyInto(dataOut, startIndex = readOffset, endIndex = readOffset + bytesToTransfer)
            bytesRead.set(bytesToTransfer)
            readOffset += bytesToTransfer
            return bytesToTransfer != 0
        }
    }

    private class WebResponseResourceHandler(
        val webResponse: WebResourceResponse,
    ) : ArrayResponseResourceHandler() {
        override fun processRequest(
            request: CefRequest,
            callback: CefCallback,
        ): Boolean {
            Log.v(TAG, "Handling request from client's response for ${request.url}")
            try {
                resolvedData = webResponse.data.readAllBytes()
                Log.v(TAG, "Resolved client response for ${resolvedData?.size} bytes")
            } catch (e: IOException) {
                Log.w(TAG, "Failed to read client data", e)
            }
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(
            response: CefResponse,
            responseLength: IntRef,
            redirectUrl: StringRef,
        ) {
            super.getResponseHeaders(response, responseLength, redirectUrl)
            webResponse.responseHeaders?.forEach { response.setHeaderByName(it.key, it.value, true) }
            response.status = webResponse.statusCode
            response.mimeType = webResponse.mimeType
        }
    }

    private class HtmlResponseResourceHandler(
        val page: CachedPage,
    ) : ArrayResponseResourceHandler() {
        override fun processRequest(
            request: CefRequest,
            callback: CefCallback,
        ): Boolean {
            Log.v(TAG, "Handling request from HTML cache for ${request.url?.sanitizedForLog()}")
            resolvedData =
                if (page.encoding != null) {
                    page.data.toByteArray(charset(page.encoding))
                } else {
                    page.data.toByteArray()
                }
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(
            response: CefResponse,
            responseLength: IntRef,
            redirectUrl: StringRef,
        ) {
            super.getResponseHeaders(response, responseLength, redirectUrl)
            if (page.mimeType != null) {
                response.mimeType = page.mimeType
            }
        }
    }

    private inner class ResourceRequestHandler(
        private val isNavigation: Boolean,
    ) : CefResourceRequestHandlerAdapter() {
        override fun onBeforeResourceLoad(
            browser: CefBrowser?,
            frame: CefFrame?,
            request: CefRequest,
        ): Boolean {
            // Initial request headers/post data only belong on the main
            // document request — not on every subresource.
            val isMainDocument =
                isNavigation && request.resourceType == CefRequest.ResourceType.RT_MAIN_FRAME
            if (isMainDocument) {
                initialRequestData?.apply(request)
                initialRequestData = null
            }

            // shouldOverrideUrlLoading is only meaningful for navigation
            // requests; consulting it for every subresource breaks page loads
            // (any non-default client answer would block images/scripts).
            val cancel =
                if (isNavigation) {
                    // TODO: we should be calling this on the handler, since CEF calls us on its IO thread
                    // thus if a client tried to use WebView#loadUrl as the docs suggest, this fails
                    viewClient.shouldOverrideUrlLoading(
                        view,
                        CefWebResourceRequest(request, frame, false),
                    )
                } else {
                    false
                }
            Log.v(TAG, "Resource ${request.url?.sanitizedForLog()}, result is cancel? $cancel")

            handler.post { viewClient.onLoadResource(view, frame?.url) }

            return cancel || settings.blockNetworkLoads
        }

        override fun getResourceHandler(
            browser: CefBrowser,
            frame: CefFrame,
            request: CefRequest,
        ): CefResourceHandler? {
            val isInitialLoad = frame.url == "" && request.method == "GET"
            Log.v(TAG, "Request ${request.method} ${request.url?.sanitizedForLog()} is initial? $isInitialLoad")
            // NOTE: we should be calling this on the handler, since CEF calls us on its IO thread
            // but docs say "This method is called on a thread other than the UI thread" so should be fine
            val response =
                if (isInitialLoad) {
                    null
                } else {
                    viewClient.shouldInterceptRequest(
                        view,
                        CefWebResourceRequest(request, frame, false),
                    )
                }
            if (response == null) {
                // prefer user's response override
                urlHttpMapping[request.url.trimEnd('/')]?.let {
                    return HtmlResponseResourceHandler(it)
                }
            }
            response ?: return null
            return WebResponseResourceHandler(response)
        }
    }

    @Suppress("DEPRECATION")
    private inner class RequestHandler : CefRequestHandlerAdapter() {
        override fun getResourceRequestHandler(
            browser: CefBrowser,
            frame: CefFrame,
            request: CefRequest,
            isNavigation: Boolean,
            isDownload: Boolean,
            requestInitiator: String,
            disableDefaultHandling: BoolRef,
        ): CefResourceRequestHandler = ResourceRequestHandler(isNavigation)

        override fun onRenderProcessTerminated(
            browser: CefBrowser,
            status: CefRequestHandler.TerminationStatus,
            errorCode: Int,
            errorString: String,
        ) {
            handler.post {
                viewClient.onRenderProcessGone(
                    view,
                    object : RenderProcessGoneDetail() {
                        override fun didCrash(): Boolean = status == CefRequestHandler.TerminationStatus.TS_PROCESS_CRASHED

                        override fun rendererPriorityAtExit(): Int = -1
                    },
                )
            }
        }
    }

    private inner class PermissionHandler : CefPermissionHandler {
        override fun onRequestMediaAccessPermission(
            browser: CefBrowser,
            frame: CefFrame,
            requestingUrl: String,
            requestedPermissions: Int,
            callback: CefMediaAccessCallback,
        ): Boolean {
            handler.post {
                Log.v(TAG, "Checking permission for $requestingUrl: $requestedPermissions")
                chromeClient.onPermissionRequest(CefPermissionRequest(requestingUrl, requestedPermissions, callback))
            }
            return true
        }
    }

    private class RenderHandler : CefRenderHandlerAdapter() {
        // Configurable off-screen viewport; CEF needs a "window" size to lay
        // out pages even though nothing is actually painted.
        override fun getViewRect(browser: CefBrowser): Rectangle =
            Rectangle(
                0,
                0,
                Integer.getInteger("suwayomi.webview.viewport.width", DEFAULT_VIEWPORT_WIDTH),
                Integer.getInteger("suwayomi.webview.viewport.height", DEFAULT_VIEWPORT_HEIGHT),
            )

        override fun onPaint(
            browser: CefBrowser,
            popup: Boolean,
            dirtyRects: Array<Rectangle>,
            buffer: ByteBuffer,
            width: Int,
            height: Int,
        ) {
            // do nothing
        }
    }

    override fun init(
        javaScriptInterfaces: Map<String, Any>?,
        privateBrowsing: Boolean,
    ) {
        Log.v(TAG, "KcefWebViewProvider: initialize")
        this.privateBrowsing = privateBrowsing

        // Honor the declared JavaScript interfaces — the parameter used to be
        // silently ignored.
        javaScriptInterfaces?.forEach { (name, obj) ->
            addJavascriptInterface(obj, name)
        }

        destroy()

        // CEF initialization can take a very long time (first run downloads
        // binaries) — never block the WebView looper on it. Navigation calls
        // arriving before init completes are queued via pendingAction.
        thread(name = "kcef-webview-init") {
            try {
                val client = runBlocking { CefHelper.createClient() }
                handler.post {
                    kcefClient =
                        client.apply {
                            addLifeSpanHandler(LifeSpanHandler())
                            addDisplayHandler(DisplayHandler())
                            addLoadHandler(LoadHandler())
                            addRequestHandler(RequestHandler())
                            addPermissionHandler(PermissionHandler())

                            val config = CefMessageRouter.CefMessageRouterConfig()
                            config.jsQueryFunction = QUERY_FN
                            config.jsCancelFunction = QUERY_CANCEL_FN
                            addMessageRouter(CefMessageRouter.create(config, MessageRouterHandler()))
                        }
                    initHandler.init(this)
                    pendingAction?.invoke()
                    pendingAction = null
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize KCEF client", e)
            }
        }
    }

    // Deprecated - should never be called
    override fun setHorizontalScrollbarOverlay(overlay: Boolean): Unit = throw UnsupportedOperationException("Stub!")

    // Deprecated - should never be called
    override fun setVerticalScrollbarOverlay(overlay: Boolean): Unit = throw UnsupportedOperationException("Stub!")

    // Deprecated - should never be called
    override fun overlayHorizontalScrollbar(): Boolean = throw UnsupportedOperationException("Stub!")

    // Deprecated - should never be called
    override fun overlayVerticalScrollbar(): Boolean = throw UnsupportedOperationException("Stub!")

    override fun getVisibleTitleHeight(): Int = throw UnsupportedOperationException("Stub!")

    override fun getCertificate(): SslCertificate = throw UnsupportedOperationException("Stub!")

    override fun setCertificate(certificate: SslCertificate): Unit = throw UnsupportedOperationException("Stub!")

    override fun savePassword(
        host: String,
        username: String,
        password: String,
    ): Unit = throw UnsupportedOperationException("Stub!")

    override fun setHttpAuthUsernamePassword(
        host: String,
        realm: String,
        username: String,
        password: String,
    ): Unit = throw UnsupportedOperationException("Stub!")

    override fun getHttpAuthUsernamePassword(
        host: String,
        realm: String,
    ): Array<String> = throw UnsupportedOperationException("Stub!")

    override fun destroy() {
        pendingAction = null
        urlHttpMapping.clear()
        browser?.close(true)
        browser?.dispose()
        browser = null
        kcefClient?.disposeWithJsHandler()
        kcefClient = null
    }

    override fun setNetworkAvailable(networkUp: Boolean): Unit = throw UnsupportedOperationException("Stub!")

    override fun saveState(outState: Bundle): WebBackForwardList = throw UnsupportedOperationException("Stub!")

    override fun savePicture(
        b: Bundle,
        dest: File,
    ): Boolean = throw UnsupportedOperationException("Stub!")

    override fun restorePicture(
        b: Bundle,
        src: File,
    ): Boolean = throw UnsupportedOperationException("Stub!")

    override fun restoreState(inState: Bundle): WebBackForwardList = throw UnsupportedOperationException("Stub!")

    /**
     * Navigate to [url], reusing the existing browser via loadURL when
     * possible. Previously every navigation destroyed and recreated the whole
     * browser, which raced with in-flight CEF callbacks.
     */
    private fun navigate(
        client: CefClient,
        url: String,
        requestData: InitialRequestData?,
    ) {
        urlHttpMapping.clear()
        chromeClient.onProgressChanged(view, 0)
        lastProgress = 0
        initialRequestData = requestData
        val existing = browser
        if (existing != null) {
            existing.loadURL(url)
        } else {
            browser =
                client
                    .createBrowser(
                        url,
                        CefRendering.CefRenderingWithHandler(renderHandler, JPanel()),
                        false,
                    ).apply {
                        // NOTE: Without this, we don't seem to be receiving any events
                        createImmediately()
                    }
        }
    }

    override fun loadUrl(
        loadUrl: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        val client = kcefClient
        if (client == null) {
            Log.w(TAG, "loadUrl before provider init; queueing navigation")
            pendingAction = { loadUrl(loadUrl, additionalHttpHeaders) }
            return
        }
        navigate(client, loadUrl, InitialRequestData(additionalHttpHeaders = additionalHttpHeaders))
        Log.d(TAG, "Page loaded at URL ${loadUrl.sanitizedForLog()}")
    }

    override fun loadUrl(url: String) {
        loadUrl(url, mapOf())
    }

    override fun postUrl(
        url: String,
        postData: ByteArray,
    ) {
        val client = kcefClient
        if (client == null) {
            Log.w(TAG, "postUrl before provider init; queueing navigation")
            pendingAction = { postUrl(url, postData) }
            return
        }
        navigate(client, url, InitialRequestData(myPostData = postData))
        Log.d(TAG, "Page posted at URL ${url.sanitizedForLog()}")
    }

    override fun loadData(
        data: String,
        mimeType: String?,
        encoding: String?,
    ) {
        loadDataWithBaseURL(null, data, mimeType, encoding, null)
    }

    override fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        val client = kcefClient
        if (client == null) {
            Log.w(TAG, "loadDataWithBaseURL before provider init; queueing navigation")
            pendingAction = { loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl) }
            return
        }
        chromeClient.onProgressChanged(view, 0)
        lastProgress = 0
        val url = baseUrl ?: "about:blank"
        // Drop stale cached payloads from earlier loadData calls.
        urlHttpMapping.clear()
        urlHttpMapping[url.trimEnd('/')] = CachedPage(data, mimeType, encoding)

        val existing = browser
        if (existing != null) {
            existing.loadURL(url)
        } else {
            browser =
                client
                    .createBrowser(
                        url,
                        CefRendering.CefRenderingWithHandler(renderHandler, JPanel()),
                        false,
                    ).apply {
                        // NOTE: Without this, we don't seem to be receiving any events
                        createImmediately()
                    }
        }
        Log.d(TAG, "Page loaded from data at base URL ${baseUrl?.sanitizedForLog()}")
    }

    override fun evaluateJavaScript(
        script: String,
        resultCallback: ValueCallback<String>?,
    ) {
        val b = browser
        if (b == null) {
            Log.w(TAG, "evaluateJavaScript called without a browser")
            resultCallback?.onReceiveValue(null)
            return
        }
        b.evaluateJavaScript(
            script.removePrefix("javascript:"),
        ) {
            Log.v(TAG, "JS returned: $it")
            // Always deliver the result (null on failure) — callers waiting on
            // the callback would otherwise hang forever.
            handler.post { resultCallback?.onReceiveValue(it) }
        }
    }

    override fun saveWebArchive(filename: String): Unit = throw UnsupportedOperationException("Stub!")

    override fun saveWebArchive(
        basename: String,
        autoname: Boolean,
        callback: ValueCallback<String>,
    ): Unit = throw UnsupportedOperationException("Stub!")

    override fun stopLoading() {
        browser?.stopLoad()
    }

    override fun reload() {
        browser?.reload()
    }

    override fun canGoBack(): Boolean = browser?.canGoBack() ?: false

    override fun goBack() {
        browser?.goBack()
    }

    override fun canGoForward(): Boolean = browser?.canGoForward() ?: false

    override fun goForward() {
        browser?.goForward()
    }

    override fun canGoBackOrForward(steps: Int): Boolean = throw UnsupportedOperationException("Stub!")

    override fun goBackOrForward(steps: Int): Unit = throw UnsupportedOperationException("Stub!")

    override fun isPrivateBrowsingEnabled(): Boolean = throw UnsupportedOperationException("Stub!")

    override fun pageUp(top: Boolean): Boolean = throw UnsupportedOperationException("Stub!")

    override fun pageDown(bottom: Boolean): Boolean = throw UnsupportedOperationException("Stub!")

    override fun insertVisualStateCallback(
        requestId: Long,
        callback: VisualStateCallback,
    ): Unit = throw UnsupportedOperationException("Stub!")

    override fun clearView(): Unit = throw UnsupportedOperationException("Stub!")

    override fun capturePicture(): Picture = throw UnsupportedOperationException("Stub!")

    override fun createPrintDocumentAdapter(documentName: String): PrintDocumentAdapter = throw UnsupportedOperationException("Stub!")

    override fun getScale(): Float = throw UnsupportedOperationException("Stub!")

    override fun setInitialScale(scaleInPercent: Int): Unit = throw UnsupportedOperationException("Stub!")

    override fun invokeZoomPicker(): Unit = throw UnsupportedOperationException("Stub!")

    override fun getHitTestResult(): HitTestResult = throw UnsupportedOperationException("Stub!")

    override fun requestFocusNodeHref(hrefMsg: Message): Unit = throw UnsupportedOperationException("Stub!")

    override fun requestImageRef(msg: Message): Unit = throw UnsupportedOperationException("Stub!")

    override fun getUrl(): String = browser?.url ?: ""

    override fun getOriginalUrl(): String = browser?.url ?: ""

    override fun getTitle(): String = lastTitle

    override fun getFavicon(): Bitmap = throw UnsupportedOperationException("Stub!")

    override fun getTouchIconUrl(): String = throw UnsupportedOperationException("Stub!")

    override fun getProgress(): Int = lastProgress

    // Off-screen rendering: the content size is not measured; report the
    // configured viewport size rather than crashing high-frequency callers.
    override fun getContentHeight(): Int = Integer.getInteger("suwayomi.webview.viewport.height", DEFAULT_VIEWPORT_HEIGHT)

    override fun getContentWidth(): Int = Integer.getInteger("suwayomi.webview.viewport.width", DEFAULT_VIEWPORT_WIDTH)

    override fun pauseTimers() {
        Log.v(TAG, "pauseTimers: doing nothing")
    }

    override fun resumeTimers() {
        Log.v(TAG, "resumeTimers: doing nothing")
    }

    override fun onPause() {
        Log.v(TAG, "onPause: doing nothing")
    }

    override fun onResume() {
        Log.v(TAG, "onResume: doing nothing")
    }

    override fun isPaused(): Boolean = false

    override fun freeMemory(): Unit = throw UnsupportedOperationException("Stub!")

    override fun clearCache(includeDiskFiles: Boolean): Unit = throw UnsupportedOperationException("Stub!")

    override fun clearFormData(): Unit = throw UnsupportedOperationException("Stub!")

    override fun clearHistory(): Unit = throw UnsupportedOperationException("Stub!")

    override fun clearSslPreferences(): Unit = throw UnsupportedOperationException("Stub!")

    override fun copyBackForwardList(): WebBackForwardList = throw UnsupportedOperationException("Stub!")

    override fun setFindListener(listener: WebView.FindListener): Unit = throw UnsupportedOperationException("Stub!")

    override fun findNext(forward: Boolean): Unit = throw UnsupportedOperationException("Stub!")

    override fun findAll(find: String): Int = throw UnsupportedOperationException("Stub!")

    override fun findAllAsync(find: String): Unit = throw UnsupportedOperationException("Stub!")

    override fun showFindDialog(
        text: String,
        showIme: Boolean,
    ): Boolean = throw UnsupportedOperationException("Stub!")

    override fun clearMatches(): Unit = throw UnsupportedOperationException("Stub!")

    override fun documentHasImages(response: Message): Unit = throw UnsupportedOperationException("Stub!")

    override fun setWebViewClient(client: WebViewClient) {
        viewClient = client
    }

    override fun getWebViewClient(): WebViewClient = viewClient

    override fun getWebViewRenderProcess(): WebViewRenderProcess? = throw UnsupportedOperationException("Stub!")

    override fun setWebViewRenderProcessClient(
        executor: Executor?,
        client: WebViewRenderProcessClient?,
    ): Unit = throw UnsupportedOperationException("Stub!")

    override fun getWebViewRenderProcessClient(): WebViewRenderProcessClient? = throw UnsupportedOperationException("Stub!")

    override fun setDownloadListener(listener: DownloadListener): Unit = throw UnsupportedOperationException("Stub!")

    override fun setWebChromeClient(client: WebChromeClient) {
        chromeClient = client
    }

    override fun getWebChromeClient(): WebChromeClient = chromeClient

    @Suppress("DEPRECATION")
    override fun setPictureListener(listener: PictureListener): Unit = throw UnsupportedOperationException("Stub!")

    /**
     * Payload for a loadDataWithBaseURL-style navigation; keeps the mimeType
     * and encoding instead of dropping them.
     */
    private class CachedPage(
        val data: String,
        val mimeType: String?,
        val encoding: String?,
    )

    @Serializable
    private data class FunctionCall(
        val interfaceName: String,
        val functionName: String,
        // JsonElement: JS callers pass numbers/booleans/objects, not only
        // strings; they are converted to the target parameter types in
        // toKotlinArgs.
        val args: Array<JsonElement> = emptyArray(),
    ) {
        /**
         * Convert the raw JSON arguments to values matching [fn]'s parameter
         * types (excluding the implicit `this` receiver at index 0).
         */
        fun toKotlinArgs(fn: KFunction<*>): Array<Any?> {
            val params = fn.parameters.drop(1)
            return Array(args.size) { i ->
                val el = args[i]
                val target = params.getOrNull(i)?.type
                el.toKotlinValue(target)
            }
        }

        private fun JsonElement.toKotlinValue(target: KType?): Any? {
            if (this is JsonNull) return null
            if (this is JsonPrimitive) {
                return when (target?.jvmErasure) {
                    Int::class, java.lang.Integer::class -> intOrNull ?: content.toIntOrNull() ?: 0
                    Long::class, java.lang.Long::class -> longOrNull ?: content.toLongOrNull() ?: 0L
                    Double::class, java.lang.Double::class -> doubleOrNull ?: content.toDoubleOrNull() ?: 0.0
                    Float::class, java.lang.Float::class -> floatOrNull ?: content.toFloatOrNull() ?: 0f
                    Boolean::class, java.lang.Boolean::class -> booleanOrNull ?: content.toBooleanStrictOrNull() ?: false
                    else -> contentOrNull ?: toString()
                }
            }
            // Objects/arrays: hand the raw JSON text to the callee.
            return toString()
        }
    }

    private data class FunctionMapping(
        val interfaceName: String,
        val functionName: String,
        val obj: Any,
        val fn: KFunction<*>,
    ) {
        fun toNice(): String = "$interfaceName.$functionName"
    }

    override fun addJavascriptInterface(
        obj: Any,
        interfaceName: String,
    ) {
        val cls = obj::class
        mappings.addAll(
            // Only expose methods explicitly annotated @JavascriptInterface —
            // exposing every declared member (incl. private/synthetic bridge
            // methods made accessible via reflection) is a serious attack
            // surface.
            cls.declaredMemberFunctions
                .filter { fn ->
                    fn.javaMethod?.getAnnotation(JavascriptInterface::class.java) != null
                }.map {
                    // This is ridiculous, but necessary, otherwise "public final" throws
                    it.javaMethod?.isAccessible = true
                    val map = FunctionMapping(interfaceName, it.name, obj, it)
                    Log.v(TAG, "Exposing: ${map.toNice()}")
                    map
                },
        )
    }

    override fun removeJavascriptInterface(interfaceName: String) {
        val removed = mappings.removeAll { it.interfaceName == interfaceName }
        Log.v(TAG, if (removed) "Removed interface mappings for $interfaceName" else "No interface mappings for $interfaceName to remove")
    }

    override fun createWebMessageChannel(): Array<WebMessagePort> = throw UnsupportedOperationException("Stub!")

    override fun postMessageToMainFrame(
        message: WebMessage,
        targetOrigin: Uri,
    ): Unit = throw UnsupportedOperationException("Stub!")

    override fun getSettings(): WebSettings = settings

    override fun setMapTrackballToArrowKeys(setMap: Boolean): Unit = throw UnsupportedOperationException("Stub!")

    override fun flingScroll(
        vx: Int,
        vy: Int,
    ): Unit = throw UnsupportedOperationException("Stub!")

    override fun getZoomControls(): View = throw UnsupportedOperationException("Stub!")

    override fun canZoomIn(): Boolean = throw UnsupportedOperationException("Stub!")

    override fun canZoomOut(): Boolean = throw UnsupportedOperationException("Stub!")

    override fun zoomBy(zoomFactor: Float): Boolean = throw UnsupportedOperationException("Stub!")

    override fun zoomIn(): Boolean = throw UnsupportedOperationException("Stub!")

    override fun zoomOut(): Boolean = throw UnsupportedOperationException("Stub!")

    override fun dumpViewHierarchyWithProperties(
        out: BufferedWriter,
        level: Int,
    ): Unit = throw UnsupportedOperationException("Stub!")

    override fun findHierarchyView(
        className: String,
        hashCode: Int,
    ): View = throw UnsupportedOperationException("Stub!")

    override fun setRendererPriorityPolicy(
        rendererRequestedPriority: Int,
        waivedWhenNotVisible: Boolean,
    ): Unit = throw UnsupportedOperationException("Stub!")

    override fun getRendererRequestedPriority(): Int = throw UnsupportedOperationException("Stub!")

    override fun getRendererPriorityWaivedWhenNotVisible(): Boolean = throw UnsupportedOperationException("Stub!")

    @SuppressWarnings("unused")
    override fun setTextClassifier(textClassifier: TextClassifier?) {
    }

    override fun getTextClassifier(): TextClassifier = TextClassifier.NO_OP

    // -------------------------------------------------------------------------
    // Provider internal methods
    // -------------------------------------------------------------------------

    override fun getViewDelegate(): ViewDelegate = throw UnsupportedOperationException("Stub!")

    override fun getScrollDelegate(): ScrollDelegate = throw UnsupportedOperationException("Stub!")

    override fun notifyFindDialogDismissed(): Unit = throw UnsupportedOperationException("Stub!")

    // -------------------------------------------------------------------------
    // View / ViewGroup delegation methods
    // -------------------------------------------------------------------------

    class KcefViewDelegate : ViewDelegate {
        override fun shouldDelayChildPressedState(): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onProvideVirtualStructure(structure: android.view.ViewStructure): Unit = throw UnsupportedOperationException("Stub!")

        override fun onProvideAutofillVirtualStructure(
            @SuppressWarnings("unused") structure: android.view.ViewStructure,
            @SuppressWarnings("unused") flags: Int,
        ) {
        }

        override fun autofill(
            @SuppressWarnings("unused") values: SparseArray<AutofillValue>,
        ) {
        }

        override fun isVisibleToUserForAutofill(
            @SuppressWarnings("unused") virtualId: Int,
        ): Boolean {
            return true // true is the default value returned by View.isVisibleToUserForAutofill()
        }

        override fun onProvideContentCaptureStructure(
            @SuppressWarnings("unused") structure: android.view.ViewStructure,
            @SuppressWarnings("unused") flags: Int,
        ) {
        }

        override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = throw UnsupportedOperationException("Stub!")

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo): Unit = throw UnsupportedOperationException("Stub!")

        override fun onInitializeAccessibilityEvent(event: AccessibilityEvent): Unit = throw UnsupportedOperationException("Stub!")

        override fun performAccessibilityAction(
            action: Int,
            arguments: Bundle,
        ): Boolean = throw UnsupportedOperationException("Stub!")

        override fun setOverScrollMode(mode: Int): Unit = throw UnsupportedOperationException("Stub!")

        override fun setScrollBarStyle(style: Int): Unit = throw UnsupportedOperationException("Stub!")

        override fun onDrawVerticalScrollBar(
            canvas: Canvas,
            scrollBar: Drawable,
            l: Int,
            t: Int,
            r: Int,
            b: Int,
        ): Unit = throw UnsupportedOperationException("Stub!")

        override fun onOverScrolled(
            scrollX: Int,
            scrollY: Int,
            clampedX: Boolean,
            clampedY: Boolean,
        ): Unit = throw UnsupportedOperationException("Stub!")

        override fun onWindowVisibilityChanged(visibility: Int): Unit = throw UnsupportedOperationException("Stub!")

        override fun onDraw(canvas: Canvas): Unit = throw UnsupportedOperationException("Stub!")

        override fun setLayoutParams(layoutParams: LayoutParams): Unit = throw UnsupportedOperationException("Stub!")

        override fun performLongClick(): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onConfigurationChanged(newConfig: Configuration): Unit = throw UnsupportedOperationException("Stub!")

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection = throw UnsupportedOperationException("Stub!")

        override fun onDragEvent(event: DragEvent): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onKeyMultiple(
            keyCode: Int,
            repeatCount: Int,
            event: KeyEvent,
        ): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onKeyDown(
            keyCode: Int,
            event: KeyEvent,
        ): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onKeyUp(
            keyCode: Int,
            event: KeyEvent,
        ): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onAttachedToWindow(): Unit = throw UnsupportedOperationException("Stub!")

        override fun onDetachedFromWindow(): Unit = throw UnsupportedOperationException("Stub!")

        override fun onMovedToDisplay(
            displayId: Int,
            config: Configuration,
        ) {
        }

        override fun onVisibilityChanged(
            changedView: View,
            visibility: Int,
        ): Unit = throw UnsupportedOperationException("Stub!")

        override fun onWindowFocusChanged(hasWindowFocus: Boolean): Unit = throw UnsupportedOperationException("Stub!")

        override fun onFocusChanged(
            focused: Boolean,
            direction: Int,
            previouslyFocusedRect: Rect,
        ): Unit = throw UnsupportedOperationException("Stub!")

        override fun setFrame(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onSizeChanged(
            w: Int,
            h: Int,
            ow: Int,
            oh: Int,
        ): Unit = throw UnsupportedOperationException("Stub!")

        override fun onScrollChanged(
            l: Int,
            t: Int,
            oldl: Int,
            oldt: Int,
        ): Unit = throw UnsupportedOperationException("Stub!")

        override fun dispatchKeyEvent(event: KeyEvent): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onTouchEvent(ev: MotionEvent): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onHoverEvent(event: MotionEvent): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onGenericMotionEvent(event: MotionEvent): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onTrackballEvent(ev: MotionEvent): Boolean = throw UnsupportedOperationException("Stub!")

        override fun requestFocus(
            direction: Int,
            previouslyFocusedRect: Rect,
        ): Boolean = throw UnsupportedOperationException("Stub!")

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ): Unit = throw UnsupportedOperationException("Stub!")

        override fun requestChildRectangleOnScreen(
            child: View,
            rect: Rect,
            immediate: Boolean,
        ): Boolean = throw UnsupportedOperationException("Stub!")

        override fun setBackgroundColor(color: Int): Unit = throw UnsupportedOperationException("Stub!")

        override fun setLayerType(
            layerType: Int,
            paint: Paint,
        ) {
            // ignore
        }

        override fun preDispatchDraw(canvas: Canvas): Unit = throw UnsupportedOperationException("Stub!")

        override fun onStartTemporaryDetach(): Unit = throw UnsupportedOperationException("Stub!")

        override fun onFinishTemporaryDetach(): Unit = throw UnsupportedOperationException("Stub!")

        override fun onActivityResult(
            requestCode: Int,
            resultCode: Int,
            data: Intent,
        ): Unit = throw UnsupportedOperationException("Stub!")

        override fun getHandler(originalHandler: Handler): Handler = throw UnsupportedOperationException("Stub!")

        override fun findFocus(originalFocusedView: View): View = throw UnsupportedOperationException("Stub!")

        @SuppressWarnings("unused")
        override fun onCheckIsTextEditor(): Boolean = false

        @SuppressWarnings("unused")
        override fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets? = null

        @SuppressWarnings("unused")
        override fun onResolvePointerIcon(
            event: MotionEvent,
            pointerIndex: Int,
        ): PointerIcon? = null
    }

    class KcefScrollDelegate : ScrollDelegate {
        override fun computeHorizontalScrollRange(): Int = throw UnsupportedOperationException("Stub!")

        override fun computeHorizontalScrollOffset(): Int = throw UnsupportedOperationException("Stub!")

        override fun computeVerticalScrollRange(): Int = throw UnsupportedOperationException("Stub!")

        override fun computeVerticalScrollOffset(): Int = throw UnsupportedOperationException("Stub!")

        override fun computeVerticalScrollExtent(): Int = throw UnsupportedOperationException("Stub!")

        override fun computeScroll(): Unit = throw UnsupportedOperationException("Stub!")
    }
}
