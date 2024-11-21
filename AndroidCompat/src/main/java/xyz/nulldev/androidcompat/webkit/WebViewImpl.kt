package xyz.nulldev.androidcompat.webkit

import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFBrowser.Companion.BLANK_URI
import dev.datlag.kcef.KCEFResourceRequestHandler
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.network.CefPostData
import org.cef.network.CefPostDataElement

class WebViewImpl {
    val settings = WebSettingsImpl()
    val kcefClient = KCEF.newClientBlocking()
    var browser: KCEFBrowser? = null
    var webViewClient: WebViewClient? = null
    var webChromeClient: WebChromeClient? = null
    var webView: WebView? = null

    fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String?, String?>,
    ) {
        browser =
            kcefClient.createBrowser(
                url,
                CefRendering.OFFSCREEN,
                context =
                    createContext(additionalHttpHeaders = additionalHttpHeaders)
                        ?: CefRequestContext.getGlobalContext(),
            )
    }

    fun loadUrl(url: String) {
        browser = kcefClient.createBrowser(url, CefRendering.OFFSCREEN)
    }

    fun postUrl(
        url: String,
        postData: ByteArray,
    ) {
        browser =
            kcefClient.createBrowser(
                url,
                CefRendering.OFFSCREEN,
                context =
                    createContext(postData = postData)
                        ?: CefRequestContext.getGlobalContext(),
            )
    }

    fun loadData(
        data: String,
        mimeType: String?,
        encoding: String?,
    ) {
        browser = kcefClient.createBrowserWithHtml(data, rendering = CefRendering.OFFSCREEN)
    }

    fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        browser = kcefClient.createBrowserWithHtml(data, baseUrl ?: BLANK_URI, CefRendering.OFFSCREEN)
    }

    fun evaluateJavascript(
        script: String,
        resultCallback: ValueCallback<String?>?,
    ) {
        browser?.evaluateJavaScript(
            script,
            callback = {
                resultCallback?.onReceiveValue(it)
            },
        )
    }

    fun setupWebViewClient(webViewClient: WebViewClient) {
        this.webViewClient = webViewClient
    }

    fun setupChromeClient(webChromeClient: WebChromeClient) {
        this.webChromeClient = webChromeClient
    }

    fun destroy() {
        kcefClient.dispose()
    }

    private fun createContext(
        additionalHttpHeaders: Map<String?, String?>? = null,
        postData: ByteArray? = null,
    ): CefRequestContext? =
        CefRequestContext.createContext { browser, frame, request, isNavigation, isDownload, requestInitiator, disableDefaultHandling ->
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
