package suwayomi.tachidesk.global.impl

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRenderHandlerAdapter
import org.cef.input.CefTouchEvent
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import uy.kohesive.injekt.injectLazy
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import javax.imageio.ImageIO
import javax.swing.JPanel

class KcefWebView {
    private val logger = KotlinLogging.logger {}
    private val renderHandler = RenderHandler()
    private var kcefClient: KCEFClient? = null
    private var browser: KCEFBrowser? = null
    private var width = 1000
    private var height = 1000

    companion object {
        private val networkHelper: NetworkHelper by injectLazy()

        fun Cookie.toCefCookie(): CefCookie {
            val cookie = this
            return CefCookie(
                cookie.name,
                cookie.value,
                if (cookie.hostOnly) {
                    cookie.domain
                } else {
                    "." + cookie.domain
                },
                cookie.path,
                cookie.secure,
                cookie.httpOnly,
                Date(),
                null,
                cookie.expiresAt < 253402300799999L, // okhttp3.internal.http.MAX_DATE
                Date(cookie.expiresAt),
            )
        }
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
        val title: String,
    ) : Event()

    @Serializable
    @SerialName("statusChange")
    private data class StatusEvent(
        val message: String,
    ) : Event()

    @Suppress("ArrayInDataClass")
    @Serializable
    @SerialName("render")
    private data class RenderEvent(
        val image: ByteArray,
    ) : Event()

    @Serializable
    @SerialName("load")
    private data class LoadEvent(
        val url: String,
        val title: String,
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
            WebView.notifyAllClients(
                Json.encodeToString<Event>(
                    ConsoleEvent(level.ordinal, message, source, line),
                ),
            )
            logger.debug { "$source:$line: $message" }
            return true
        }

        override fun onAddressChange(
            browser: CefBrowser,
            frame: CefFrame,
            url: String,
        ) {
            if (!frame.isMain) return
            this@KcefWebView.browser!!.evaluateJavaScript("return document.title") {
                WebView.notifyAllClients(
                    Json.encodeToString<Event>(
                        AddressEvent(url, it ?: ""),
                    ),
                )
            }
            flush()
        }

        override fun onStatusMessage(
            browser: CefBrowser,
            value: String,
        ) {
            WebView.notifyAllClients(
                Json.encodeToString<Event>(
                    StatusEvent(value),
                ),
            )
        }
    }

    private inner class LoadHandler : CefLoadHandlerAdapter() {
        override fun onLoadEnd(
            browser: CefBrowser,
            frame: CefFrame,
            httpStatusCode: Int,
        ) {
            logger.info { "Load event: ${frame.name} - ${frame.url}" }
            if (httpStatusCode > 0 && frame.isMain) handleLoad(frame.url, httpStatusCode)
            flush()
        }

        override fun onLoadError(
            browser: CefBrowser,
            frame: CefFrame,
            errorCode: CefLoadHandler.ErrorCode,
            errorText: String,
            failedUrl: String,
        ) {
            if (frame.isMain) handleLoad(failedUrl, 0, errorText)
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
            var image = myImage ?: BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)

            if (image.width != width || image.height != height) {
                image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
            }

            val dst = (image.raster.getDataBuffer() as DataBufferInt).getData()
            val src = buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
            src.get(dst)

            myImage = image
            val stream = ByteArrayOutputStream()
            val success = ImageIO.write(myImage, "png", stream)
            if (!success) {
                throw IllegalStateException("Failed to convert image to PNG")
            }

            WebView.notifyAllClients(
                Json.encodeToString<Event>(
                    RenderEvent(stream.toByteArray()),
                ),
            )
        }
    }

    init {
        destroy()
        kcefClient =
            KCEF.newClientBlocking().apply {
                addDisplayHandler(DisplayHandler())
                addLoadHandler(LoadHandler())
            }

        logger.info { "Start loading cookies" }
        CefCookieManager.getGlobalManager().apply {
            val cookies = networkHelper.cookieStore.getStoredCookies()
            for (cookie in cookies) {
                try {
                    if (!setCookie(
                            "https://" + cookie.domain,
                            cookie.toCefCookie(),
                        )
                    ) {
                        throw Exception()
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Loading cookie ${cookie.name} failed" }
                }
            }
        }
    }

    fun destroy() {
        flush()
        browser?.close(true)
        browser?.dispose()
        browser = null
        kcefClient?.dispose()
        kcefClient = null
    }

    fun loadUrl(url: String) {
        browser?.close(true)
        browser?.dispose()
        browser =
            kcefClient!!
                .createBrowser(
                    url,
                    CefRendering.CefRenderingWithHandler(renderHandler, JPanel()),
                    // NOTE: with a context, we don't seem to be getting any cookies
                ).apply {
                    // NOTE: Without this, we don't seem to be receiving any events
                    createImmediately()
                }
    }

    fun resize(
        width: Int,
        height: Int,
    ) {
        this.width = width
        this.height = height
        browser?.wasResized(width, height)
    }

    private fun flush() {
        if (browser == null) return
        logger.info { "Start cookie flush" }
        CefCookieManager.getGlobalManager().visitAllCookies { it, _, _, _ ->
            try {
                networkHelper.cookieStore.addAll(
                    HttpUrl
                        .Builder()
                        .scheme("http")
                        .host(it.domain.removePrefix("."))
                        .build(),
                    listOf(
                        Cookie
                            .Builder()
                            .name(it.name)
                            .value(it.value)
                            .path(if (it.path.startsWith('/')) it.path else "/" + it.path)
                            .domain(it.domain.removePrefix("."))
                            .apply {
                                if (it.hasExpires) {
                                    expiresAt(it.expires.time)
                                } else {
                                    expiresAt(Long.MAX_VALUE)
                                }
                                if (it.httponly) {
                                    httpOnly()
                                }
                                if (it.secure) {
                                    secure()
                                }
                                if (!it.domain.startsWith('.')) {
                                    hostOnlyDomain(it.domain.removePrefix("."))
                                }
                            }.build(),
                    ),
                )
            } catch (e: Exception) {
                logger.warn(e) { "Writing cookie ${it.name} failed" }
            }
            return@visitAllCookies true
        }
    }

    private fun keyEvent(
        msg: WebView.JsEventMessage,
        component: Component,
        id: Int,
        modifier: Int,
    ): KeyEvent? {
        val char = if (msg.key?.length == 1) msg.key[0] else KeyEvent.CHAR_UNDEFINED
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
                        "Delete" -> KeyEvent.VK_DELETE
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
            if (char == KeyEvent.CHAR_UNDEFINED && code != KeyEvent.VK_ENTER) return null
            return KeyEvent(
                component,
                id,
                0L,
                modifier,
                KeyEvent.VK_UNDEFINED,
                if (code == KeyEvent.VK_ENTER) code.toChar() else char,
                KeyEvent.KEY_LOCATION_UNKNOWN,
            )
        }
        return KeyEvent(
            component,
            id,
            0L,
            modifier,
            code,
            if (code == KeyEvent.VK_ENTER) code.toChar() else char,
            KeyEvent.KEY_LOCATION_STANDARD,
        )
    }

    fun event(msg: WebView.JsEventMessage) {
        val component = browser?.uiComponent ?: return
        val type = msg.eventType
        val clickX = msg.clickX
        val clickY = msg.clickY
        val modifier =
            (
                (if (msg.altKey == true) InputEvent.ALT_DOWN_MASK else 0) or
                    (if (msg.ctrlKey == true) InputEvent.CTRL_DOWN_MASK else 0) or
                    (if (msg.shiftKey == true) InputEvent.SHIFT_DOWN_MASK else 0) or
                    (if (msg.metaKey == true) InputEvent.META_DOWN_MASK else 0)
            )

        if (type == "wheel") {
            val d = msg.deltaY?.toInt() ?: 1
            val ev =
                MouseWheelEvent(
                    component,
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
            browser!!.sendKeyEvent(keyEvent(msg, component, KeyEvent.KEY_PRESSED, modifier)!!)
            keyEvent(msg, component, KeyEvent.KEY_TYPED, modifier)?.let { browser!!.sendKeyEvent(it) }
            return
        }
        if (type == "keyup") {
            browser!!.sendKeyEvent(keyEvent(msg, component, KeyEvent.KEY_RELEASED, modifier)!!)
            return
        }
        if (type == "mousedown" || type == "mouseup" || type == "click") {
            val id =
                when (type) {
                    "mousedown" -> MouseEvent.MOUSE_PRESSED
                    "mouseup" -> MouseEvent.MOUSE_PRESSED
                    "click" -> MouseEvent.MOUSE_CLICKED
                    else -> 0
                }
            val mouseModifier =
                when (msg.button ?: 0) {
                    0 -> MouseEvent.BUTTON1_DOWN_MASK
                    1 -> MouseEvent.BUTTON2_DOWN_MASK
                    2 -> MouseEvent.BUTTON3_DOWN_MASK
                    else -> 0
                }
            val button =
                when (msg.button ?: 0) {
                    0 -> MouseEvent.BUTTON1
                    1 -> MouseEvent.BUTTON2
                    2 -> MouseEvent.BUTTON3
                    else -> 0
                }
            val ev =
                MouseEvent(
                    component,
                    id,
                    0L,
                    modifier or mouseModifier,
                    clickX.toInt(),
                    clickY.toInt(),
                    msg.clientX?.toInt() ?: 0,
                    msg.clientY?.toInt() ?: 0,
                    1,
                    true,
                    button,
                )
            browser!!.sendMouseEvent(ev)
            val evType =
                when (type) {
                    "mousedown" -> CefTouchEvent.EventType.PRESSED
                    "mouseup" -> CefTouchEvent.EventType.RELEASED
                    else -> CefTouchEvent.EventType.MOVED
                }
            val ev2 =
                CefTouchEvent(
                    0,
                    clickX,
                    clickY,
                    10.0f,
                    10.0f,
                    0.0f,
                    1.0f,
                    evType,
                    modifier,
                    CefTouchEvent.PointerType.MOUSE,
                )
            browser!!.sendTouchEvent(ev2)
            return
        }
        if (type == "mousemove") {
            val ev =
                MouseEvent(
                    component,
                    MouseEvent.MOUSE_MOVED,
                    0L,
                    modifier,
                    clickX.toInt(),
                    clickY.toInt(),
                    msg.clientX?.toInt() ?: 0,
                    msg.clientY?.toInt() ?: 0,
                    0,
                    true,
                    0,
                )
            browser!!.sendMouseEvent(ev)
            return
        }
    }

    fun canGoBack(): Boolean = browser!!.canGoBack()

    fun goBack() {
        browser!!.goBack()
    }

    fun canGoForward(): Boolean = browser!!.canGoForward()

    fun goForward() {
        browser!!.goForward()
    }

    private fun handleLoad(
        url: String,
        status: Int = 0,
        error: String? = null,
    ) {
        browser!!.evaluateJavaScript("return document.title") {
            logger.info { "Load finished with title $it" }
            WebView.notifyAllClients(
                Json.encodeToString<Event>(
                    LoadEvent(url, it ?: "", status, error),
                ),
            )
        }
    }
}
