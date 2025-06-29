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
            if (httpStatusCode > 0)
                    handleLoad(
                            frame,
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
            handleLoad(frame, failedUrl, "load", mapOf("error" to errorText))
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

    public fun event(element: String, type: String, detail: String) {
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
            frame: CefFrame,
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
                    for (let img of document.querySelectorAll('img')) {
                        img.src = img.src;
                    }
                    for (let link of document.querySelectorAll('link[href]')) {
                        link.href = link.href;
                    }
                    for (let a of document.querySelectorAll('a[href]')) {
                        a.href = a.href;
                        a.target = "";
                    }
                    const style = document.createElement('style');
                    style.textContent = "noscript{display:none}";
                    document.head.appendChild(style);
                    let html = "";
                    const title = document.title;
                    try {
                        html = new XMLSerializer().serializeToString(document.doctype) + document.documentElement.outerHTML;
                    } catch (e) {
                    }
                    window.${QUERY_FN}({
                        request: JSON.stringify({
                            type: ${Json.encodeToString(ev)},
                            frame: ${Json.encodeToString(frame.name)},
                            title,
                            url: ${Json.encodeToString(url)},
                            html,${serializedArgs}
                        }),
                        persistent: false,
                    });

                    const computePath = (node) => {
                        let path = '';
                        while (node) {
                            const classes = Array.from(node.classList).map(x => `.${"$"}{x}`).join("");
                            const id = node.id ? `#${"$"}{node.id}` : "";
                            const idx = node.parentElement ? Array.from(node.parentElement.children).indexOf(node) : -1;
                            const idx1 = idx >= 0 ? `:nth-child(${"$"}{idx + 1})` : "";
                            if (path) path = " > " + path;
                            path = `${"$"}{node.tagName}${"$"}{id}${"$"}{classes}${"$"}{idx1}` + path;
                            node = node.parentElement;
                        }
                        return path;
                    };

                    const observer = new MutationObserver((changes) => {
                        // TODO: This could be cleaner, we're recreating the entire parent
                        // if just one node is added or removed
                        const toSend = changes.map(change => ({
                            type: change.type,
                            target: computePath(change.target),
                            attributeName: change.attributeName,
                            attributeNamspace: change.attributeNamespace,
                            oldValue: change.oldValue,
                            newValue: change.type === "attributes" ?
                                change.target.getAttributeNS(change.attributeNamespace, change.attributeName) :
                                change.type === "characterData" ? change.target.data : change.target.innerHTML,
                        }));
                        window.${QUERY_FN}({
                            request: JSON.stringify({
                                type: 'docMutate',
                                frame: ${Json.encodeToString(frame.name)},
                                title,
                                changes: toSend,
                            }),
                            persistent: false,
                        })
                    });
                    observer.observe(document.documentElement, { subtree: true, childList: true, attributes: true, characterData: true });
                })();
                """
        logger.info { "Load finished for URL $url" }
        frame.executeJavaScript(js, url, 0)
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
