package ireader.core.http

import okhttp3.Cookie
import okhttp3.Headers

interface  BrowserEngineInterface {
    suspend fun fetch(
        url: String,
        selector: String? = null,
        headers: Headers? = null,
        timeout: Long = 50000L,
        userAgent: String = DEFAULT_USER_AGENT
    ): Result
}




expect class BrowserEngine : BrowserEngineInterface {
    override suspend fun fetch(
        url: String,
        selector: String?,
        headers: Headers?,
        timeout: Long,
        userAgent: String,
    ): Result
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
