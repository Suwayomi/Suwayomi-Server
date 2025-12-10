package ireader.core.http

import com.fleeksoft.ksoup.nodes.Document

/**
 * Desktop implementation of WebViewManager
 * Currently a stub - could be enhanced with JavaFX WebView or JCEF
 */
 class WebViewManger {
     var isInit: Boolean = false
     var userAgent: String = DEFAULT_USER_AGENT
     var selector: String? = null
     var html: Document? = null
     var webUrl: String? = null
     var inProgress: Boolean = false

     fun init(): Any {
        return 0
    }

     fun update() {
        // No-op on desktop
    }

     fun destroy() {
        // No-op on desktop
    }
    
     fun loadInBackground(url: String, selector: String?, onReady: (String) -> Unit) {
        // Not supported on desktop
        onReady("")
    }
    
     fun isProcessingInBackground(): Boolean = false
    
     fun isAvailable(): Boolean = false
}
