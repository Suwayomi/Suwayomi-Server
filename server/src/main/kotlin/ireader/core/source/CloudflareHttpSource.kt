package ireader.core.source

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import ireader.core.http.CloudflareBypassFailed
import ireader.core.http.cloudflare.BypassConfig
import ireader.core.http.cloudflare.BypassResult
import ireader.core.http.cloudflare.CloudflareBypassManager
import ireader.core.http.cloudflare.CloudflareCookieStore
import ireader.core.http.cloudflare.CloudflareDetector
import ireader.core.http.cloudflare.ClearanceCookie
import ireader.core.http.cloudflare.InMemoryCloudfareCookieStore
import ireader.core.http.fingerprint.BrowserFingerprint
import ireader.core.http.fingerprint.FingerprintManager
import ireader.core.http.ratelimit.RateLimiter
import ireader.core.log.Log
import ireader.core.source.model.Command
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo

/**
 * Enhanced HttpSource with built-in Cloudflare bypass support
 * Extend this class for sources that use Cloudflare protection
 */
abstract class CloudflareHttpSource(
    dependencies: Dependencies
) : HttpSource(dependencies) {
    
    /**
     * Cloudflare bypass manager - override to provide custom implementation
     */
    protected open val cloudflareBypass: CloudflareBypassManager? = null
    
    /**
     * Rate limiter - override to provide custom implementation
     */
    protected open val rateLimiter: RateLimiter? = null
    
    /**
     * Fingerprint manager - override to provide custom implementation
     */
    protected open val fingerprintManager: FingerprintManager? = null
    
    /**
     * Cookie store for Cloudflare cookies
     */
    protected open val cookieStore: CloudflareCookieStore = InMemoryCloudfareCookieStore()
    
    /**
     * Whether to automatically handle Cloudflare challenges
     */
    protected open val autoBypassCloudflare: Boolean = true
    
    /**
     * Maximum bypass attempts
     */
    protected open val maxBypassAttempts: Int = 3

    /**
     * Get the browser fingerprint for this source
     */
    protected fun getFingerprint(): BrowserFingerprint {
        return fingerprintManager?.getOrCreateProfile(baseUrl) 
            ?: BrowserFingerprint.DEFAULT
    }
    
    /**
     * Get cached Cloudflare cookie if available
     */
    protected suspend fun getCachedCookie(): ClearanceCookie? {
        val domain = baseUrl.extractDomain()
        return cookieStore.getClearanceCookie(domain)
    }
    
    /**
     * Apply Cloudflare cookies to a request builder
     */
    protected suspend fun HttpRequestBuilder.applyCloudflareCookies() {
        val cookie = getCachedCookie()
        if (cookie != null) {
            cookie("cf_clearance", cookie.cfClearance)
            cookie.cfBm?.let { cookie("__cf_bm", it) }
            header(HttpHeaders.UserAgent, cookie.userAgent)
        } else {
            header(HttpHeaders.UserAgent, getFingerprint().userAgent)
        }
    }
    
    /**
     * Fetch URL with automatic Cloudflare bypass
     */
    protected suspend fun fetchWithBypass(
        url: String,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): HttpResponse {
        // Apply rate limiting
        rateLimiter?.acquire(url.extractDomain())
        
        // Build request with cookies
        val response = client.get(url) {
            applyCloudflareCookies()
            requestBuilder()
        }
        
        // Notify rate limiter of response
        rateLimiter?.onResponse(url.extractDomain(), response.status.value)
        
        // Check for Cloudflare challenge
        if (autoBypassCloudflare && CloudflareDetector.isChallengeLikely(response)) {
            return handleCloudflareChallenge(url, response, requestBuilder)
        }
        
        return response
    }
    
    /**
     * Handle Cloudflare challenge with bypass
     */
    private suspend fun handleCloudflareChallenge(
        url: String,
        initialResponse: HttpResponse,
        requestBuilder: HttpRequestBuilder.() -> Unit
    ): HttpResponse {
        val bypass = cloudflareBypass ?: throw CloudflareBypassFailed(
            "Cloudflare protection detected but no bypass manager configured"
        )
        
        var lastResponse = initialResponse
        var attempts = 0
        
        while (attempts < maxBypassAttempts) {
            attempts++
            
            val body = try {
                lastResponse.bodyAsText()
            } catch (e: Exception) {
                ""
            }
            
            val bypassConfig = BypassConfig(
                userAgent = getFingerprint().userAgent,
                timeout = 60000L
            )
            
            val result = bypass.bypass(url, lastResponse, body, bypassConfig)
            
            when (result) {
                is BypassResult.Success, is BypassResult.CachedCookie -> {
                    // Retry request with new cookies
                    val cookie = result.extractCookie()!!
                    
                    rateLimiter?.acquire(url.extractDomain())
                    
                    val retryResponse = client.get(url) {
                        cookie("cf_clearance", cookie.cfClearance)
                        cookie.cfBm?.let { cookie("__cf_bm", it) }
                        header(HttpHeaders.UserAgent, cookie.userAgent)
                        requestBuilder()
                    }
                    
                    rateLimiter?.onResponse(url.extractDomain(), retryResponse.status.value)
                    
                    // Check if still challenged
                    if (!CloudflareDetector.isChallengeLikely(retryResponse)) {
                        return retryResponse
                    }
                    
                    // Still challenged, invalidate cookie and retry
                    cookieStore.invalidate(url.extractDomain())
                    lastResponse = retryResponse
                }
                
                is BypassResult.NotNeeded -> {
                    return lastResponse
                }
                
                is BypassResult.UserInteractionRequired -> {
                    throw CloudflareBypassFailed(
                        "User interaction required: ${result.message}"
                    )
                }
                
                is BypassResult.Failed -> {
                    if (!result.canRetry || attempts >= maxBypassAttempts) {
                        throw CloudflareBypassFailed(result.reason)
                    }
                    Log.debug { "Bypass attempt $attempts failed: ${result.reason}" }
                }
            }
        }
        
        throw CloudflareBypassFailed("Max bypass attempts ($maxBypassAttempts) exceeded")
    }

    /**
     * Fetch URL and parse as Jsoup document with Cloudflare bypass
     */
    protected suspend fun fetchDocument(
        url: String,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): com.fleeksoft.ksoup.nodes.Document {
        val response = fetchWithBypass(url, requestBuilder)
        return response.asJsoup()
    }
    
    /**
     * Fetch URL and return body text with Cloudflare bypass
     */
    protected suspend fun fetchText(
        url: String,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): String {
        val response = fetchWithBypass(url, requestBuilder)
        return response.bodyAsText()
    }
    
    /**
     * Clear cached Cloudflare cookies for this source
     */
    suspend fun clearCloudflareCookies() {
        cookieStore.invalidate(baseUrl.extractDomain())
    }
    
    /**
     * Check if source is currently protected by Cloudflare
     */
    suspend fun isCloudflareProtected(): Boolean {
        return try {
            val response = client.get(baseUrl) {
                header(HttpHeaders.UserAgent, getFingerprint().userAgent)
            }
            CloudflareDetector.isChallengeLikely(response)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getCapabilities(): SourceCapabilities {
        return SourceCapabilities(
            supportsLatest = true,
            supportsSearch = true,
            supportsFilters = true,
            supportsDeepLinks = false,
            supportsCommands = false
        )
    }
    
    private fun String.extractDomain(): String {
        return this.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }
}
