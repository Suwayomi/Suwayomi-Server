package ireader.core.http

import ireader.core.http.cloudflare.BrowserEngineInterface
import ireader.core.http.cloudflare.CloudflareBypassManager
import ireader.core.http.cloudflare.CloudflareCookieStore
import ireader.core.http.cloudflare.CookieReplayStrategy
import ireader.core.http.cloudflare.FlareSolverrClient
import ireader.core.http.cloudflare.FlareSolverrStrategy
import ireader.core.http.cloudflare.InMemoryCloudfareCookieStore
import ireader.core.http.cloudflare.WebViewBypassStrategy
import ireader.core.http.fingerprint.FingerprintManager
import ireader.core.http.fingerprint.InMemoryFingerprintManager
import ireader.core.http.ratelimit.AdaptiveRateLimiter
import ireader.core.http.ratelimit.RateLimitConfig
import ireader.core.http.ratelimit.RateLimiter
import ireader.core.http.ratelimit.DefaultRequestQueue
import ireader.core.http.ratelimit.RequestQueue
import ireader.core.http.ratelimit.RequestQueueConfig
import ireader.core.prefs.PreferenceStore
import ireader.core.http.cloudflare.PersistentCloudfareCookieStore

/**
 * Factory for creating HTTP clients with enhanced features
 */
object HttpClientsFactory {
    
    /**
     * Create an EnhancedHttpClientsWrapper with all features enabled
     */
    fun createEnhanced(
        baseClients: HttpClientsInterface,
        preferenceStore: PreferenceStore? = null,
        flareSolverrUrl: String? = null
    ): EnhancedHttpClientsInterface {
        // Create cookie store (persistent if preferences available)
        val cookieStore: CloudflareCookieStore = if (preferenceStore != null) {
            PersistentCloudfareCookieStore(preferenceStore)
        } else {
            InMemoryCloudfareCookieStore()
        }
        
        // Create fingerprint manager
        val fingerprintManager = InMemoryFingerprintManager()
        
        // Create rate limiter
        val rateLimiter = AdaptiveRateLimiter(
            defaultConfig = RateLimitConfig(
                requestsPerSecond = 2.0,
                burstSize = 5,
                adaptiveEnabled = true
            )
        )
        
        // Create request queue
        val requestQueue = DefaultRequestQueue(
            config = RequestQueueConfig(
                maxConcurrentPerDomain = 2,
                maxConcurrentTotal = 10
            ),
            rateLimiter = rateLimiter
        )
        
        // Build bypass strategies
        val strategies = buildList {
            // Always add cookie replay (highest priority)
            add(CookieReplayStrategy(cookieStore))
            
            // Add WebView strategy if browser engine is available
            if (baseClients.browser.isAvailable()) {
                add(WebViewBypassStrategy(baseClients.browser, fingerprintManager))
            }
            
            // Add FlareSolverr strategy if URL is configured (for desktop)
            // Note: FlareSolverr client would need to be created with an HttpClient
            // This is a simplified version - in production you'd inject the client
        }
        
        // Create bypass manager
        val bypassManager = CloudflareBypassManager(
            strategies = strategies,
            cookieStore = cookieStore
        )
        
        return EnhancedHttpClientsWrapper(
            delegate = baseClients,
            rateLimiter = rateLimiter,
            requestQueue = requestQueue,
            cloudflareBypass = bypassManager,
            fingerprintManager = fingerprintManager,
            cookieStore = cookieStore
        )
    }

    /**
     * Create a minimal EnhancedHttpClientsWrapper with just rate limiting
     */
    fun createMinimal(
        baseClients: HttpClientsInterface
    ): EnhancedHttpClientsInterface {
        val cookieStore = InMemoryCloudfareCookieStore()
        val fingerprintManager = InMemoryFingerprintManager()
        val rateLimiter = AdaptiveRateLimiter()
        
        return EnhancedHttpClientsWrapper(
            delegate = baseClients,
            rateLimiter = rateLimiter,
            requestQueue = null,
            cloudflareBypass = null,
            fingerprintManager = fingerprintManager,
            cookieStore = cookieStore
        )
    }
    
    /**
     * Create bypass manager with custom strategies
     */
    fun createBypassManager(
        cookieStore: CloudflareCookieStore,
        browserEngine: BrowserEngineInterface? = null,
        fingerprintManager: FingerprintManager? = null,
        flareSolverrClient: FlareSolverrClient? = null
    ): CloudflareBypassManager {
        val strategies = buildList {
            add(CookieReplayStrategy(cookieStore))
            
            if (browserEngine != null && browserEngine.isAvailable()) {
                add(WebViewBypassStrategy(browserEngine, fingerprintManager))
            }
            
            if (flareSolverrClient != null) {
                add(FlareSolverrStrategy(flareSolverrClient))
            }
        }
        
        return CloudflareBypassManager(
            strategies = strategies,
            cookieStore = cookieStore
        )
    }
}

/**
 * Extension function to upgrade HttpClientsInterface to EnhancedHttpClientsInterface
 */
fun HttpClientsInterface.enhanced(
    preferenceStore: PreferenceStore? = null
): EnhancedHttpClientsInterface {
    return HttpClientsFactory.createEnhanced(this, preferenceStore)
}

/**
 * Extension function to create minimal enhanced wrapper
 */
fun HttpClientsInterface.withRateLimiting(): EnhancedHttpClientsInterface {
    return HttpClientsFactory.createMinimal(this)
}
