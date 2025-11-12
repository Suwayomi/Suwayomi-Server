package ireader.core.http

import okhttp3.Cookie
import okhttp3.Headers

interface BrowserEngineInterface {
    suspend fun fetch(
        url: String,
        selector: String? = null,
        headers: Headers? = null,
        timeout: Long = 50000L,
        userAgent: String = ireader.core.http.DEFAULT_USER_AGENT,
    ): Result
}

/**
 * Stub implementation of BrowserEngine for JVM
 * IReader extensions that need browser functionality won't work,
 * but basic HTTP sources will function
 */
class BrowserEngine : BrowserEngineInterface {
    override suspend fun fetch(
        url: String,
        selector: String?,
        headers: Headers?,
        timeout: Long,
        userAgent: String,
    ): Result {
        // Stub implementation - browser features not supported on server
        throw UnsupportedOperationException("Browser engine is not supported on server. Use HttpClient instead.")
    }
}

/**
 * This object is representing the result of an request
 * @param responseBody - the response responseBody
 * @param responseStatus - the http responses status code and message
 * @param contentType - the http responses content type
 * @param responseHeader - the http responses headers
 * @param cookies - the http response's cookies
 */
@Suppress("LongParameterList")
class Result(
    val responseBody: String,
    val cookies: List<Cookie> = emptyList(),
)
