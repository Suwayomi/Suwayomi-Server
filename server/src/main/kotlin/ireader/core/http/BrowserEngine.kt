package ireader.core.http

import ireader.core.http.cloudflare.BrowserEngineInterface

/**
 * Desktop implementation of BrowserEngine
 * Currently a stub - could be enhanced with JavaFX WebView or JCEF
 */
 class BrowserEngine  constructor() : BrowserEngineInterface {
     override suspend fun fetch(
        url: String,
        selector: String?,
        headers: Headers,
        timeout: Long,
        userAgent: String
    ): BrowserEngineInterface.BrowserResult {
       return BrowserEngineInterface.BrowserResult(
           responseBody = "",
           cookies = emptyList(),
           statusCode = 501,
           error = "BrowserEngine not available on desktop platform. Consider using JavaFX WebView or JCEF for full browser capabilities."
       )
    }
    
     override fun isAvailable(): Boolean = false
}
