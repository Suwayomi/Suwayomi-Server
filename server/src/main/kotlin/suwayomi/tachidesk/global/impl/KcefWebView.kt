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
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseWheelEvent
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

    private fun keyEvent(
        msg: WebView.JsEventMessage,
        id: Int,
        modifier: Int,
    ): KeyEvent? {
        val char = if (msg.key?.length == 1) msg.key.get(0) else KeyEvent.CHAR_UNDEFINED
        val code =
            when (char.uppercaseChar()) {
                in 'A'..'Z', in '0'..'9' -> char.uppercaseChar().code
                '&' -> KeyEvent.VK_AMPERSAND
                '*' -> KeyEvent.VK_ASTERISK
                '@' -> KeyEvent.VK_AT
                '\\' -> KeyEvent.VK_BACK_SLASH
                '{' -> KeyEvent.VK_BRACELEFT
                '}' -> KeyEvent.VK_BRACERIGHT
                '^' -> KeyEvent.VK_CIRCUMFLEX
                ']' -> KeyEvent.VK_CLOSE_BRACKET
                ':' -> KeyEvent.VK_COLON
                ',' -> KeyEvent.VK_COMMA
                '$' -> KeyEvent.VK_DOLLAR
                '=' -> KeyEvent.VK_EQUALS
                '€' -> KeyEvent.VK_EURO_SIGN
                '!' -> KeyEvent.VK_EXCLAMATION_MARK
                '>' -> KeyEvent.VK_GREATER
                '(' -> KeyEvent.VK_LEFT_PARENTHESIS
                '<' -> KeyEvent.VK_LESS
                '-' -> KeyEvent.VK_MINUS
                '#' -> KeyEvent.VK_NUMBER_SIGN
                '[' -> KeyEvent.VK_OPEN_BRACKET
                '.' -> KeyEvent.VK_PERIOD
                '+' -> KeyEvent.VK_PLUS
                '\'' -> KeyEvent.VK_QUOTE
                '"' -> KeyEvent.VK_QUOTEDBL
                ')' -> KeyEvent.VK_RIGHT_PARENTHESIS
                ';' -> KeyEvent.VK_SEMICOLON
                '/' -> KeyEvent.VK_SLASH
                ' ' -> KeyEvent.VK_SPACE
                '_' -> KeyEvent.VK_UNDERSCORE
                else ->
                    when (msg.key) {
                        "Alt" -> KeyEvent.VK_ALT
                        "Backspace" -> KeyEvent.VK_BACK_SPACE
                        "CapsLock" -> KeyEvent.VK_CAPS_LOCK
                        "Control" -> KeyEvent.VK_CONTROL
                        "ArrowDown" -> KeyEvent.VK_DOWN
                        "End" -> KeyEvent.VK_END
                        "Enter" -> KeyEvent.VK_ENTER
                        "Escape" -> KeyEvent.VK_ESCAPE
                        "F1" -> KeyEvent.VK_F1
                        "F2" -> KeyEvent.VK_F2
                        "F3" -> KeyEvent.VK_F3
                        "F4" -> KeyEvent.VK_F4
                        "F5" -> KeyEvent.VK_F5
                        "F6" -> KeyEvent.VK_F6
                        "F7" -> KeyEvent.VK_F7
                        "F8" -> KeyEvent.VK_F8
                        "F9" -> KeyEvent.VK_F9
                        "F10" -> KeyEvent.VK_F10
                        "F11" -> KeyEvent.VK_F11
                        "F12" -> KeyEvent.VK_F12
                        "Home" -> KeyEvent.VK_HOME
                        "Insert" -> KeyEvent.VK_INSERT
                        "ArrowLeft" -> KeyEvent.VK_LEFT
                        "Meta" -> KeyEvent.VK_META
                        "NumLock" -> KeyEvent.VK_NUM_LOCK
                        "PageDown" -> KeyEvent.VK_PAGE_DOWN
                        "PageUp" -> KeyEvent.VK_PAGE_UP
                        "Pause" -> KeyEvent.VK_PAUSE
                        "ArrowRight" -> KeyEvent.VK_RIGHT
                        "ScrollLock" -> KeyEvent.VK_SCROLL_LOCK
                        "Shift" -> KeyEvent.VK_SHIFT
                        "Tab" -> KeyEvent.VK_TAB
                        "ArrowUp" -> KeyEvent.VK_UP
                        else -> KeyEvent.VK_UNDEFINED
                    }
            }
        if (id == KeyEvent.KEY_TYPED) {
            if (char == KeyEvent.CHAR_UNDEFINED) return null
            logger.info { "key $char ${msg.key} undefined" }
            return KeyEvent(browser!!.uiComponent, id, 0L, modifier, KeyEvent.VK_UNDEFINED, char, KeyEvent.KEY_LOCATION_UNKNOWN)
        }
        logger.info { "key $char ${msg.key} $code" }
        return KeyEvent(browser!!.uiComponent, id, 0L, modifier, code, char, KeyEvent.KEY_LOCATION_UNKNOWN)
    }

    public fun event(msg: WebView.JsEventMessage) {
        val type = msg.eventType
        val clickX = msg.clickX
        val clickY = msg.clickY
        val detail = msg.toJsConstructor()
        val modifier = (
            (if (msg.altKey ?: false) InputEvent.ALT_DOWN_MASK else 0) or
                (if (msg.ctrlKey ?: false) InputEvent.CTRL_DOWN_MASK else 0) or
                (if (msg.shiftKey ?: false) InputEvent.SHIFT_DOWN_MASK else 0)
        )

        if (type == "wheel") {
            val d = msg.deltaY?.toInt() ?: 1
            val ev =
                MouseWheelEvent(
                    browser!!.uiComponent,
                    0,
                    0L,
                    modifier,
                    clickX.toInt(),
                    clickY.toInt(),
                    0,
                    false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                    -d,
                    1,
                )
            browser!!.sendMouseWheelEvent(ev)
            return
        }
        if (type == "keydown") {
            browser!!.sendKeyEvent(keyEvent(msg, KeyEvent.KEY_PRESSED, modifier)!!)
            keyEvent(msg, KeyEvent.KEY_TYPED, modifier)?.let {
                browser!!.sendKeyEvent(it)
            }
            return
        }
        if (type == "keyup") {
            browser!!.sendKeyEvent(keyEvent(msg, KeyEvent.KEY_RELEASED, modifier)!!)
            return
        }

        val constructor =
            when (type) {
                "click", "mousedown", "mouseup", "mousemove" -> "MouseEvent"
                "wheel" -> "WheelEvent"
                "keydown", "keyup" -> "KeyboardEvent"
                "submit" -> "SubmitEvent"
                "focus", "blur" -> "FocusEvent"
                else -> "Event"
            }
        val js =
            """
                (function() {
                    const detail = $detail;
                    const e = document.elementFromPoint($clickX, $clickY)
                    const ev = new $constructor(${Json.encodeToString(type)}, detail);
                    e.dispatchEvent(ev);
                    e.focus();
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
