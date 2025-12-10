package ireader.core.http

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import ireader.core.http.cloudflare.BypassConfig
import ireader.core.http.cloudflare.BypassResult
import ireader.core.http.cloudflare.CloudflareBypassManager
import ireader.core.http.cloudflare.CloudflareCookieStore
import ireader.core.http.cloudflare.CloudflareDetector
import ireader.core.http.cloudflare.InMemoryCloudfareCookieStore
import ireader.core.http.fingerprint.BrowserFingerprint
import ireader.core.http.fingerprint.FingerprintManager
import ireader.core.http.fingerprint.InMemoryFingerprintManager
import ireader.core.http.ratelimit.AdaptiveRateLimiter
import ireader.core.http.ratelimit.RateLimiter
import ireader.core.http.ratelimit.RequestQueue
import ireader.core.source.FetchError
import ireader.core.source.FetchResult

/**
 * Enhanced HTTP clients interface with Cloudflare bypass, rate limiting, and fingerprint management
 */
interface EnhancedHttpClientsInterface : HttpClientsInterface {
    /** Rate limiter for controlling request frequency */
    val rateLimiter: RateLimiter
    
    /** Request queue for managing concurrent requests */
    val requestQueue: RequestQueue?
    
    /** Cloudflare bypass manager */
    val cloudflareBypass: CloudflareBypassManager?
    
    /** Fingerprint manager for consistent browser identity */
    val fingerprintManager: FingerprintManager
    
    /** Cloudflare cookie store */
    val cookieStore: CloudflareCookieStore
    
    /**
     * Fetch URL with automatic Cloudflare bypass
     */
    suspend fun fetchWithBypass(
        url: String,
        config: FetchConfig = FetchConfig()
    ): FetchResult<String>
    
    /**
     * Fetch URL and return response with automatic Cloudflare bypass
     */
    suspend fun fetchResponseWithBypass(
        url: String,
        config: FetchConfig = FetchConfig()
    ): FetchResult<HttpResponse>
}


/**
 * Configuration for fetch operations
 */
data class FetchConfig(
    /** Request timeout in milliseconds */
    val timeout: Long = 30000,
    /** Number of retry attempts */
    val retries: Int = 3,
    /** Whether to automatically bypass Cloudflare */
    val bypassCloudflare: Boolean = true,
    /** Whether to use rate limiter */
    val useRateLimiter: Boolean = true,
    /** Custom headers */
    val headers: Map<String, String> = emptyMap(),
    /** Custom user agent (overrides fingerprint) */
    val userAgent: String? = null,
    /** Custom fingerprint */
    val fingerprint: BrowserFingerprint? = null
)

/**
 * Default implementation of EnhancedHttpClientsInterface
 * Wraps an existing HttpClientsInterface and adds enhanced features
 */
class EnhancedHttpClientsWrapper(
    private val delegate: HttpClientsInterface,
    override val rateLimiter: RateLimiter = AdaptiveRateLimiter(),
    override val requestQueue: RequestQueue? = null,
    override val cloudflareBypass: CloudflareBypassManager? = null,
    override val fingerprintManager: FingerprintManager = InMemoryFingerprintManager(),
    override val cookieStore: CloudflareCookieStore = InMemoryCloudfareCookieStore()
) : EnhancedHttpClientsInterface {
    
    // Delegate to wrapped interface
    override val browser: BrowserEngine get() = delegate.browser
    override val default: HttpClient get() = delegate.default
    override val cloudflareClient: HttpClient get() = delegate.cloudflareClient
    override val config: NetworkConfig get() = delegate.config
    override val sslConfig: SSLConfiguration get() = delegate.sslConfig
    override val cookieSynchronizer: CookieSynchronizer get() = delegate.cookieSynchronizer
    
    override suspend fun fetchWithBypass(
        url: String,
        config: FetchConfig
    ): FetchResult<String> {
        val responseResult = fetchResponseWithBypass(url, config)
        
        return when (responseResult) {
            is FetchResult.Success -> {
                try {
                    val body = responseResult.data.bodyAsText()
                    FetchResult.Success(body)
                } catch (e: Exception) {
                    FetchResult.Error(
                        FetchError.ParsingError(
                            message = "Failed to read response body: ${e.message}",
                            url = url,
                            cause = e
                        )
                    )
                }
            }
            is FetchResult.Error -> responseResult
        }
    }

    override suspend fun fetchResponseWithBypass(
        url: String,
        config: FetchConfig
    ): FetchResult<HttpResponse> {
        val domain = url.extractDomain()
        
        // Apply rate limiting
        if (config.useRateLimiter) {
            rateLimiter.acquire(domain)
        }
        
        // Get fingerprint
        val fingerprint = config.fingerprint 
            ?: fingerprintManager.getOrCreateProfile(domain)
        val userAgent = config.userAgent ?: fingerprint.userAgent
        
        // Check for cached Cloudflare cookie
        val cachedCookie = cookieStore.getClearanceCookie(domain)
        
        return try {
            // Make initial request
            val response = default.get(url) {
                // Apply headers
                header(HttpHeaders.UserAgent, userAgent)
                config.headers.forEach { (key, value) ->
                    header(key, value)
                }
                
                // Apply cached Cloudflare cookies
                if (cachedCookie != null && cachedCookie.userAgent == userAgent) {
                    cookie("cf_clearance", cachedCookie.cfClearance)
                    cachedCookie.cfBm?.let { cookie("__cf_bm", it) }
                }
            }
            
            // Notify rate limiter
            if (config.useRateLimiter) {
                rateLimiter.onResponse(domain, response.status.value)
            }
            
            // Check for Cloudflare challenge
            if (config.bypassCloudflare && CloudflareDetector.isChallengeLikely(response)) {
                handleCloudflareChallenge(url, response, config, fingerprint)
            } else {
                FetchResult.Success(response)
            }
        } catch (e: Exception) {
            FetchResult.Error(
                FetchError.NetworkError(
                    message = e.message ?: "Network request failed",
                    cause = e
                )
            )
        }
    }
    
    private suspend fun handleCloudflareChallenge(
        url: String,
        initialResponse: HttpResponse,
        config: FetchConfig,
        fingerprint: BrowserFingerprint
    ): FetchResult<HttpResponse> {
        val bypass = cloudflareBypass ?: return FetchResult.Error(
            FetchError.NetworkError(
                message = "Cloudflare protection detected but no bypass manager configured",
                statusCode = initialResponse.status.value
            )
        )
        
        val domain = url.extractDomain()
        val userAgent = config.userAgent ?: fingerprint.userAgent
        
        val body = try {
            initialResponse.bodyAsText()
        } catch (e: Exception) {
            ""
        }
        
        val bypassConfig = BypassConfig(
            userAgent = userAgent,
            timeout = config.timeout
        )
        
        val result = bypass.bypass(url, initialResponse, body, bypassConfig)
        
        return when (result) {
            is BypassResult.Success, is BypassResult.CachedCookie -> {
                val cookie = result.extractCookie()!!
                
                // Retry with cookies
                if (config.useRateLimiter) {
                    rateLimiter.acquire(domain)
                }
                
                try {
                    val retryResponse = default.get(url) {
                        header(HttpHeaders.UserAgent, cookie.userAgent)
                        cookie("cf_clearance", cookie.cfClearance)
                        cookie.cfBm?.let { cookie("__cf_bm", it) }
                        config.headers.forEach { (key, value) ->
                            header(key, value)
                        }
                    }
                    
                    if (config.useRateLimiter) {
                        rateLimiter.onResponse(domain, retryResponse.status.value)
                    }
                    
                    FetchResult.Success(retryResponse)
                } catch (e: Exception) {
                    FetchResult.Error(
                        FetchError.NetworkError(
                            message = "Retry after bypass failed: ${e.message}",
                            cause = e
                        )
                    )
                }
            }
            
            is BypassResult.NotNeeded -> {
                FetchResult.Success(initialResponse)
            }
            
            is BypassResult.UserInteractionRequired -> {
                FetchResult.Error(
                    FetchError.AuthError(
                        message = result.message,
                        requiresLogin = false
                    )
                )
            }
            
            is BypassResult.Failed -> {
                FetchResult.Error(
                    FetchError.NetworkError(
                        message = "Cloudflare bypass failed: ${result.reason}",
                        statusCode = initialResponse.status.value
                    )
                )
            }
        }
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
