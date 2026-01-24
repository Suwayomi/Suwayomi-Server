package ireader.core.http

/**
 * Base exception for HTTP-related errors.
 * Replaces okio.IOException for KMP compatibility.
 */
open class HttpException(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when a network request fails
 */
class NetworkException(
    message: String? = null,
    cause: Throwable? = null
) : HttpException(message, cause)

/**
 * Exception thrown when a request times out
 */
class TimeoutException(
    message: String? = null,
    cause: Throwable? = null
) : HttpException(message, cause)

/**
 * Exception thrown for SSL/TLS errors
 */
class SSLException(
    message: String? = null,
    cause: Throwable? = null
) : HttpException(message, cause)

/**
 * Exception thrown when Cloudflare bypass fails
 */
class CloudflareBypassFailed(
    message: String? = "Cloudflare bypass failed",
    cause: Throwable? = null
) : HttpException(message, cause)

/**
 * Exception thrown when a WebView is required
 */
class NeedWebView(
    message: String? = "WebView required",
    cause: Throwable? = null
) : HttpException(message, cause)

/**
 * Exception thrown when WebView is out of date
 */
class OutOfDateWebView(
    message: String? = "WebView is out of date",
    cause: Throwable? = null
) : HttpException(message, cause)
