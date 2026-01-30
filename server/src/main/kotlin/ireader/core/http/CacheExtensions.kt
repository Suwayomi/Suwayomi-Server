package ireader.core.http

import io.ktor.client.request.*
import io.ktor.util.*

/**
 * Attribute key for per-request cache control
 */
val CacheControlAttribute = AttributeKey<CacheControl>("CacheControl")

/**
 * Cache control options for individual requests
 */
data class CacheControl(
    /**
     * Whether to use cache for this request
     */
    val useCache: Boolean = true,
    
    /**
     * Custom cache duration for this request (overrides default)
     */
    val cacheDurationMs: Long? = null,
    
    /**
     * Force refresh (bypass cache)
     */
    val forceRefresh: Boolean = false
)

/**
 * Extension to disable cache for a specific request
 */
fun HttpRequestBuilder.noCache() {
    attributes.put(CacheControlAttribute, CacheControl(useCache = false))
}

/**
 * Extension to force refresh (bypass cache) for a specific request
 */
fun HttpRequestBuilder.forceRefresh() {
    attributes.put(CacheControlAttribute, CacheControl(forceRefresh = true))
}

/**
 * Extension to set custom cache duration for a specific request
 */
fun HttpRequestBuilder.cacheFor(durationMs: Long) {
    attributes.put(CacheControlAttribute, CacheControl(cacheDurationMs = durationMs))
}

/**
 * Example usage in sources:
 * 
 * ```kotlin
 * // Disable cache for this request
 * client.get(requestBuilder(url).apply { noCache() })
 * 
 * // Force refresh (bypass cache)
 * client.get(requestBuilder(url).apply { forceRefresh() })
 * 
 * // Cache for 10 minutes instead of default
 * client.get(requestBuilder(url).apply { cacheFor(10 * 60 * 1000) })
 * ```
 */
