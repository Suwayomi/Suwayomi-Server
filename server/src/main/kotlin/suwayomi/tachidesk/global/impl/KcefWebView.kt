package suwayomi.tachidesk.global.impl

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import dev.datlag.kcef.KCEFResourceRequestHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRenderHandlerAdapter
import org.cef.network.CefPostData
import org.cef.network.CefPostDataElement
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import javax.swing.JPanel
import kotlin.collections.Map

class KcefWebView {
    private val logger = KotlinLogging.logger {}
    private val renderHandler = RenderHandler()
    private var kcefClient: KCEFClient? = null
    private var browser: KCEFBrowser? = null
    private var width = 1000
    private var height = 1000

    companion object {
        const val QUERY_FN = "__\$_suwayomiWebQuery"
        const val QUERY_CANCEL_FN = "__\$_suwayomiWebQueryCancel"
    }

    @Serializable sealed class Event

    @Serializable
    @SerialName("consoleMessage")
    private data class ConsoleEvent(
        val severity: Int,
        val message: String,
        val source: String,
        val line: Int,
    ) : Event()

    @Serializable
    @SerialName("addressChange")
    private data class AddressEvent(
        val url: String,
    ) : Event()

    @Serializable
    @SerialName("statusChange")
    private data class StatusEvent(
        val message: String,
    ) : Event()

    @Serializable
    @SerialName("render")
    // TODO: page title
    private data class RenderEvent(
        val image: ByteArray,
    ) : Event()

    @Serializable
    @SerialName("load")
    // TODO: page title
    private data class LoadEvent(
        val url: String,
        val status: Int = 0,
        val error: String? = null,
    ) : Event()

    private inner class DisplayHandler : CefDisplayHandlerAdapter() {
        override fun onConsoleMessage(
            browser: CefBrowser,
            level: CefSettings.LogSeverity,
            message: String,
            source: String,
            line: Int,
        ): Boolean {
            val ev: Event = ConsoleEvent(level.ordinal, message, source, line)
            WebView.notifyAllClients(Json.encodeToString(ev))
            logger.debug { "$source:$line: $message" }
            return true
        }

        override fun onAddressChange(
            browser: CefBrowser,
            frame: CefFrame,
            url: String,
        ) {
            if (!frame.isMain()) return
            val ev: Event = AddressEvent(url)
            WebView.notifyAllClients(Json.encodeToString(ev))
        }

        override fun onStatusMessage(
            browser: CefBrowser,
            value: String,
        ) {
            val ev: Event = StatusEvent(value)
            WebView.notifyAllClients(Json.encodeToString(ev))
        }
    }

    private inner class LoadHandler : CefLoadHandlerAdapter() {
        override fun onLoadEnd(
            browser: CefBrowser,
            frame: CefFrame,
            httpStatusCode: Int,
        ) {
            logger.info { "Load event: ${frame.name} - ${frame.url}" }
            if (httpStatusCode > 0) handleLoad(frame.url, httpStatusCode)
        }

        override fun onLoadError(
            browser: CefBrowser,
            frame: CefFrame,
            errorCode: CefLoadHandler.ErrorCode,
            errorText: String,
            failedUrl: String,
        ) {
            handleLoad(failedUrl, 0, errorText)
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
            // TODO: should we really pass everything to the client?
            WebView.notifyAllClients(request)
            return true
        }
    }

    // Loosely based on
    // https://github.com/JetBrains/jcef/blob/main/java/org/cef/browser/CefBrowserOsr.java
    private inner class RenderHandler : CefRenderHandlerAdapter() {
        var myImage: BufferedImage? = null

        override fun getViewRect(browser: CefBrowser): Rectangle = Rectangle(0, 0, width, height)

        override fun onPaint(
            browser: CefBrowser,
            popup: Boolean,
            dirtyRects: Array<Rectangle>,
            buffer: ByteBuffer,
            width: Int,
            height: Int,
        ) {
            logger.info { "PAINT $width $height" }
            var image = myImage ?: BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)

            if (image.width != width || image.height != height) {
                image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
            }

            val dst = (image.getRaster().getDataBuffer() as DataBufferInt).getData()
            val src = buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
            src.get(dst)

            myImage = image
            val success = ImageIO.write(image, "png", File("/tmp/test.png"))
            if (!success) {
                throw IllegalStateException("Failed to convert image to PNG")
            }

            val stream = ByteArrayOutputStream()
            ImageIO.write(renderHandler.myImage, "png", stream)

            val ev: Event = RenderEvent(stream.toByteArray())
            WebView.notifyAllClients(Json.encodeToString(ev))
        }
    }

    init {
        destroy()
        kcefClient =
            KCEF.newClientBlocking().apply {
                addDisplayHandler(DisplayHandler())
                addLoadHandler(LoadHandler())

                val config = CefMessageRouter.CefMessageRouterConfig()
                config.jsQueryFunction = QUERY_FN
                config.jsCancelFunction = QUERY_CANCEL_FN
                addMessageRouter(CefMessageRouter.create(config, MessageRouterHandler()))
            }
    }

    public fun destroy() {
        browser?.close(true)
        browser?.dispose()
        browser = null
        kcefClient?.dispose()
        kcefClient = null
    }

    public fun loadUrl(
        loadUrl: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        browser?.close(true)
        browser?.dispose()
        browser =
            kcefClient!!
                .createBrowser(
                    loadUrl,
                    CefRendering.CefRenderingWithHandler(renderHandler, JPanel()),
                    context = createContext(additionalHttpHeaders),
                ).apply {
                    // NOTE: Without this, we don't seem to be receiving any events
                    createImmediately()
                }
    }

    public fun loadUrl(url: String) {
        loadUrl(url, mapOf())
    }

    public fun resize(
        width: Int,
        height: Int,
    ) {
        this.width = width
        this.height = height
        browser?.wasResized(width, height)
    }

    public fun event(
        element: String,
        type: String,
        detail: String,
    ) {
        return
        val constructor =
            when (type) {
                "click", "mousedown", "mouseup", "mousemove" -> "MouseEvent"
                "keydown", "keyup" -> "KeyboardEvent"
                "submit" -> "SubmitEvent"
                "focus", "blur" -> "FocusEvent"
                else -> "Event"
            }
        val js =
            """
                (function() {
                    try {
                        const detail = $detail;
                        const e = document.querySelector(${Json.encodeToString(element)});
                        const ev = new $constructor(${Json.encodeToString(type)}, detail);
                        e.dispatchEvent(ev);
                        if (typeof detail.inputValueAfter !== 'undefined' && detail.inputValueAfter !== null) {
                            e.value = detail.inputValueAfter;
                        }
                    } catch (e) {
                        console.error(e);
                        // send a doc-change event since we're clearly out of sync
                        const title = document.title;
                        try {
                            html = document.documentElement.innerHTML;
                        } catch (e) {
                        }
                        window.${QUERY_FN}({
                            request: JSON.stringify({
                                type: 'docChange',
                                title,
                                html,
                            }),
                            persistent: false,
                        })
                    }
                })();
                """
        browser!!.executeJavaScript(js, browser!!.url, 0)
    }

    public fun canGoBack(): Boolean = browser!!.canGoBack()

    public fun goBack() {
        browser!!.goBack()
    }

    public fun canGoForward(): Boolean = browser!!.canGoForward()

    public fun goForward() {
        browser!!.goForward()
    }

    private fun handleLoad(
        url: String,
        status: Int = 0,
        error: String? = null,
    ) {
        val ev: Event = LoadEvent(url, status, error)
        WebView.notifyAllClients(Json.encodeToString(ev))
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
            disableDefaultHandling,
            ->
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
