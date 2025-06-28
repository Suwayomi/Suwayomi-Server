package suwayomi.tachidesk.global.impl

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import dev.datlag.kcef.KCEFResourceRequestHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.collections.Map
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
import org.cef.network.CefPostData
import org.cef.network.CefPostDataElement

class KcefWebView {
    private val logger = KotlinLogging.logger {}
    private var kcefClient: KCEFClient? = null
    private var browser: KCEFBrowser? = null

    companion object {
        const val QUERY_FN = "__\$_suwayomiWebQuery"
        const val QUERY_CANCEL_FN = "__\$_suwayomiWebQueryCancel"
    }

    @Serializable sealed class Event {}

    @Serializable
    @SerialName("consoleMessage")
    private data class ConsoleEvent(
            val severity: Int,
            val message: String,
            val source: String,
            val line: Int
    ) : Event()
    @Serializable
    @SerialName("addressChange")
    private data class AddressEvent(val url: String) : Event()
    @Serializable
    @SerialName("statusChange")
    private data class StatusEvent(val message: String) : Event()

    private inner class DisplayHandler : CefDisplayHandlerAdapter() {

        override fun onConsoleMessage(
                browser: CefBrowser,
                level: CefSettings.LogSeverity,
                message: String,
                source: String,
                line: Int,
        ): Boolean {
            WebView.notifyAllClients(
                    Json.encodeToString(ConsoleEvent(level.ordinal, message, source, line))
            )
            return true
        }

        override fun onAddressChange(
                browser: CefBrowser,
                frame: CefFrame,
                url: String,
        ) {
            WebView.notifyAllClients(Json.encodeToString(AddressEvent(url)))
        }

        override fun onStatusMessage(
                browser: CefBrowser,
                value: String,
        ) {
            WebView.notifyAllClients(Json.encodeToString(StatusEvent(value)))
        }
    }

    private inner class LoadHandler : CefLoadHandlerAdapter() {
        override fun onLoadEnd(
                browser: CefBrowser,
                frame: CefFrame,
                httpStatusCode: Int,
        ) {
            if (httpStatusCode > 0)
                    handleLoad(
                            browser,
                            frame.url,
                            "load",
                            mapOf("status" to httpStatusCode.toString())
                    )
        }

        override fun onLoadError(
                browser: CefBrowser,
                frame: CefFrame,
                errorCode: CefLoadHandler.ErrorCode,
                errorText: String,
                failedUrl: String,
        ) {
            handleLoad(browser, failedUrl, "load", mapOf("error" to errorText))
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
                kcefClient!!.createBrowser(
                                loadUrl,
                                CefRendering.OFFSCREEN,
                                context = createContext(additionalHttpHeaders),
                        )
                        .apply {
                            // NOTE: Without this, we don't seem to be receiving any events
                            createImmediately()
                        }
    }

    public fun loadUrl(url: String) {
        loadUrl(url, mapOf())
    }

    public fun click(element: String, buttons: Int) {
        val js =
                """
                (function() {
                    const e = document.querySelector(${Json.encodeToString(element)});
                    const ev = new MouseEvent('click', { buttons: $buttons });
                    e.dispatchEvent(ev);
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
            browser: CefBrowser,
            url: String,
            ev: String,
            args: Map<String, String> = mapOf()
    ) {
        val serializedArgs =
                args.asIterable().fold("") { str, v ->
                    str +
                            "\n        ${Json.encodeToString(v.key)}: ${Json.encodeToString(v.value)},"
                }

        val js =
                """
                (function() {
                    // NOTE: reading .src and .href normalizes to qualified URL (with origin)
                    for (let img in document.querySelectorAll('img')) {
                        img.src = img.src;
                    }
                    for (let a in document.querySelectorAll('a[href]')) {
                        a.href = a.href;
                        a.target = "_blank";
                    }
                    let html = "";
                    const title = document.title;
                    try {
                        html = new XMLSerializer().serializeToString(document.doctype) + document.documentElement.outerHTML;
                    } catch (e) {
                    }
                    window.${QUERY_FN}({
                        request: JSON.stringify({
                            type: ${Json.encodeToString(ev)},
                            title,
                            html,${serializedArgs}
                        }),
                        persistent: false,
                    })
                })();
                """
        logger.info { "Load finished for URL $url" }
        browser.executeJavaScript(js, url, 0)
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
