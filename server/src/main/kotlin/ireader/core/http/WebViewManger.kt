package ireader.core.http

import org.jsoup.Jsoup

/**
 * Stub implementation of WebViewManager for JVM
 * WebView functionality is not supported on server
 */
class WebViewManger {
    var isInit: Boolean = false
    var userAgent: String = ireader.core.http.DEFAULT_USER_AGENT
    var selector: String? = null
    var html: org.jsoup.nodes.Document = Jsoup.parse("")
    var webUrl: String? = null
    var inProgress: Boolean = false

    fun init(): Any = throw UnsupportedOperationException("WebView is not supported on server")

    fun update(): Unit = throw UnsupportedOperationException("WebView is not supported on server")

    fun destroy() {
        // Nothing to destroy in stub implementation
    }
}
