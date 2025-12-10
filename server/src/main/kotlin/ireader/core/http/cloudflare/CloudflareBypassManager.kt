package ireader.core.http.cloudflare

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import ireader.core.log.Log

/**
 * Manages Cloudflare bypass using multiple strategies
 */
class CloudflareBypassManager(
    private val strategies: List<CloudflareBypassStrategy>,
    private val cookieStore: CloudflareCookieStore,
    private val defaultConfig: BypassConfig = BypassConfig()
) {
    
    /**
     * Attempt to bypass Cloudflare protection
     * 
     * @param url The URL being accessed
     * @param response The HTTP response that triggered the bypass
     * @param config Optional bypass configuration
     * @return BypassResult indicating success or failure
     */
    suspend fun bypass(
        url: String,
        response: HttpResponse,
        config: BypassConfig = defaultConfig
    ): BypassResult {
        val body = try {
            response.bodyAsText()
        } catch (e: Exception) {
            ""
        }
        
        return bypass(url, response, body, config)
    }
    
    /**
     * Attempt to bypass Cloudflare protection with pre-fetched body
     */
    suspend fun bypass(
        url: String,
        response: HttpResponse,
        body: String,
        config: BypassConfig = defaultConfig
    ): BypassResult {
        // Detect challenge type
        val challenge = CloudflareDetector.detect(response, body)
        
        if (challenge is CloudflareChallenge.None) {
            return BypassResult.NotNeeded
        }
        
        Log.debug { "Cloudflare challenge detected: ${challenge::class.simpleName} for $url" }
        
        return bypassChallenge(url, challenge, config)
    }

    /**
     * Attempt to bypass a known challenge type
     */
    suspend fun bypassChallenge(
        url: String,
        challenge: CloudflareChallenge,
        config: BypassConfig = defaultConfig
    ): BypassResult {
        val domain = url.extractDomain()
        
        // First, check if we have a valid cached cookie
        val cachedCookie = cookieStore.getClearanceCookie(domain)
        if (cachedCookie != null && cookieStore.isValid(cachedCookie)) {
            // Verify user agent matches
            if (config.userAgent == null || config.userAgent == cachedCookie.userAgent) {
                Log.debug { "Using cached Cloudflare cookie for $domain" }
                return BypassResult.CachedCookie(cachedCookie)
            }
        }
        
        // Sort strategies by priority (highest first)
        val sortedStrategies = strategies.sortedByDescending { it.priority }
        
        // Try each strategy
        for (strategy in sortedStrategies) {
            if (!strategy.canHandle(challenge)) {
                Log.debug { "Strategy ${strategy.name} cannot handle ${challenge::class.simpleName}" }
                continue
            }
            
            Log.debug { "Trying bypass strategy: ${strategy.name}" }
            
            val result = try {
                strategy.bypass(url, challenge, config)
            } catch (e: Exception) {
                Log.error { "Strategy ${strategy.name} threw exception" }
                BypassResult.Failed(
                    "Strategy ${strategy.name} failed: ${e.message}",
                    challenge,
                    canRetry = true
                )
            }
            
            when (result) {
                is BypassResult.Success -> {
                    // Save the cookie for future use
                    cookieStore.saveClearanceCookie(domain, result.cookie)
                    Log.debug { "Bypass successful with strategy: ${strategy.name}" }
                    return result
                }
                is BypassResult.CachedCookie -> {
                    Log.debug { "Using cached cookie from strategy: ${strategy.name}" }
                    return result
                }
                is BypassResult.NotNeeded -> {
                    return result
                }
                is BypassResult.UserInteractionRequired -> {
                    // Don't try other strategies if user interaction is needed
                    return result
                }
                is BypassResult.Failed -> {
                    if (!result.canRetry) {
                        Log.debug { "Strategy ${strategy.name} failed (no retry): ${result.reason}" }
                        return result
                    }
                    Log.debug { "Strategy ${strategy.name} failed (will try next): ${result.reason}" }
                    // Continue to next strategy
                }
            }
        }
        
        // All strategies failed
        return BypassResult.Failed(
            "All bypass strategies failed for ${challenge::class.simpleName}",
            challenge,
            canRetry = false
        )
    }

    /**
     * Check if a URL is likely protected by Cloudflare
     */
    suspend fun isProtected(response: HttpResponse): Boolean {
        return CloudflareDetector.isChallengeLikely(response)
    }
    
    /**
     * Get cached cookie for a domain if available
     */
    suspend fun getCachedCookie(domain: String): ClearanceCookie? {
        return cookieStore.getClearanceCookie(domain)
    }
    
    /**
     * Invalidate cached cookie for a domain
     */
    suspend fun invalidateCookie(domain: String) {
        cookieStore.invalidate(domain)
    }
    
    /**
     * Clear all cached cookies
     */
    suspend fun clearAllCookies() {
        cookieStore.clearAll()
    }
    
    /**
     * Get list of available strategies
     */
    fun getAvailableStrategies(): List<String> {
        return strategies.map { it.name }
    }
    
    /**
     * Check if any strategy can handle a challenge type
     */
    suspend fun canHandle(challenge: CloudflareChallenge): Boolean {
        return strategies.any { it.canHandle(challenge) }
    }
    
    companion object {
        /**
         * Create a bypass manager with default strategies
         */
        fun createDefault(cookieStore: CloudflareCookieStore): CloudflareBypassManager {
            return CloudflareBypassManager(
                strategies = listOf(
                    CookieReplayStrategy(cookieStore)
                    // Platform-specific strategies are added in actual implementations
                ),
                cookieStore = cookieStore
            )
        }
    }
}
