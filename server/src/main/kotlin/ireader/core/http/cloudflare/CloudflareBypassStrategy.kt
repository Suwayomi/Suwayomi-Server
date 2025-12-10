package ireader.core.http.cloudflare

/**
 * Strategy interface for bypassing Cloudflare protection
 */
interface CloudflareBypassStrategy {
    /** Priority of this strategy (higher = tried first) */
    val priority: Int
    
    /** Human-readable name of this strategy */
    val name: String
    
    /**
     * Check if this strategy can handle the given challenge
     */
    suspend fun canHandle(challenge: CloudflareChallenge): Boolean
    
    /**
     * Attempt to bypass the Cloudflare challenge
     */
    suspend fun bypass(
        url: String,
        challenge: CloudflareChallenge,
        config: BypassConfig
    ): BypassResult
}

/**
 * Configuration for bypass attempts
 */
data class BypassConfig(
    val timeout: Long = 60000L,
    val userAgent: String? = null,
    val proxy: ProxyConfig? = null,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000L
)

/**
 * Proxy configuration
 */
data class ProxyConfig(
    val host: String,
    val port: Int,
    val type: ProxyType = ProxyType.HTTP,
    val username: String? = null,
    val password: String? = null
)

enum class ProxyType { HTTP, SOCKS4, SOCKS5 }

/**
 * Result of a bypass attempt
 */
sealed class BypassResult {
    /** Bypass successful, cookie obtained */
    data class Success(val cookie: ClearanceCookie) : BypassResult()
    
    /** Used cached cookie (no bypass needed) */
    data class CachedCookie(val cookie: ClearanceCookie) : BypassResult()
    
    /** No bypass needed (not a Cloudflare challenge) */
    object NotNeeded : BypassResult()

    /** Bypass failed */
    data class Failed(
        val reason: String,
        val challenge: CloudflareChallenge? = null,
        val canRetry: Boolean = false
    ) : BypassResult()
    
    /** User interaction required */
    data class UserInteractionRequired(
        val challenge: CloudflareChallenge,
        val message: String
    ) : BypassResult()
    
    /**
     * Check if bypass was successful
     */
    fun isSuccess(): Boolean = this is Success || this is CachedCookie || this is NotNeeded
    
    /**
     * Get the cookie if available
     */
    fun extractCookie(): ClearanceCookie? = when (this) {
        is Success -> cookie
        is CachedCookie -> cookie
        else -> null
    }
    
    /**
     * Get error message if failed
     */
    fun extractErrorMessage(): String? = when (this) {
        is Failed -> reason
        is UserInteractionRequired -> message
        else -> null
    }
}

/**
 * Cookie replay strategy - reuses valid cached cookies
 */
class CookieReplayStrategy(
    private val cookieStore: CloudflareCookieStore
) : CloudflareBypassStrategy {
    
    override val priority = 200 // Highest priority - try cached cookies first
    override val name = "CookieReplay"
    
    override suspend fun canHandle(challenge: CloudflareChallenge): Boolean {
        // Can handle any challenge if we have a valid cookie
        return challenge !is CloudflareChallenge.None
    }
    
    override suspend fun bypass(
        url: String,
        challenge: CloudflareChallenge,
        config: BypassConfig
    ): BypassResult {
        val domain = url.extractDomain()
        val cookie = cookieStore.getClearanceCookie(domain)
        
        return if (cookie != null && cookieStore.isValid(cookie)) {
            // Check if user agent matches (important for Cloudflare)
            if (config.userAgent == null || config.userAgent == cookie.userAgent) {
                BypassResult.CachedCookie(cookie)
            } else {
                // User agent mismatch - cookie won't work
                BypassResult.Failed(
                    "Cached cookie exists but user agent doesn't match",
                    challenge,
                    canRetry = true
                )
            }
        } else {
            BypassResult.Failed(
                "No valid cached cookie available",
                challenge,
                canRetry = true
            )
        }
    }
}

/**
 * Extract domain from URL
 */
fun String.extractDomain(): String {
    return this.lowercase()
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .substringBefore("/")
        .substringBefore(":")
}
