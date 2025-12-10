package ireader.core.http

/**
 * Desktop implementation of cookie synchronization
 * Desktop doesn't have WebView, so this is a no-op implementation
 */
 class CookieSynchronizer {
     fun syncFromWebView(url: String) {
        // No-op: Desktop doesn't have WebView
    }
    
     fun syncToWebView(url: String) {
        // No-op: Desktop doesn't have WebView
    }
    
     fun clearAll() {
        // No-op: Desktop doesn't have WebView
    }
}
