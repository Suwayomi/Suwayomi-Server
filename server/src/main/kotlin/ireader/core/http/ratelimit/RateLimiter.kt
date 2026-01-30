package ireader.core.http.ratelimit

import ireader.core.util.currentTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rate limiter interface for controlling request frequency
 */
interface RateLimiter {
    /**
     * Acquire permission to make a request (suspends until allowed)
     */
    suspend fun acquire(domain: String, weight: Int = 1)
    
    /**
     * Try to acquire permission without waiting
     * @return true if permission granted, false otherwise
     */
    fun tryAcquire(domain: String, weight: Int = 1): Boolean
    
    /**
     * Set rate limit configuration for a domain
     */
    fun setLimit(domain: String, config: RateLimitConfig)
    
    /**
     * Get current stats for a domain
     */
    fun getStats(domain: String): RateLimitStats
    
    /**
     * Notify the rate limiter of a response (for adaptive limiting)
     */
    fun onResponse(domain: String, statusCode: Int, retryAfter: Long? = null)
    
    /**
     * Reset rate limiter state for a domain
     */
    fun reset(domain: String)
    
    /**
     * Reset all rate limiter state
     */
    fun resetAll()
}

/**
 * Rate limit configuration
 */
data class RateLimitConfig(
    /** Maximum requests per second */
    val requestsPerSecond: Double = 2.0,
    /** Maximum burst size (requests that can be made immediately) */
    val burstSize: Int = 5,
    /** Minimum delay between requests in milliseconds */
    val minDelayMs: Long = 100,
    /** Maximum delay when rate limited */
    val maxDelayMs: Long = 10000,
    /** Enable adaptive rate limiting based on server responses */
    val adaptiveEnabled: Boolean = true
)

/**
 * Rate limit statistics
 */
data class RateLimitStats(
    val domain: String,
    val requestsPerSecond: Double,
    val availableTokens: Double,
    val consecutiveErrors: Int,
    val lastRequestTime: Long,
    val isThrottled: Boolean
)

/**
 * Adaptive rate limiter using token bucket algorithm
 * Automatically adjusts rate based on server responses
 */
class AdaptiveRateLimiter(
    private val defaultConfig: RateLimitConfig = RateLimitConfig()
) : RateLimiter {
    
    private val states = mutableMapOf<String, RateLimitState>()
    private val mutex = Mutex()
    
    private data class RateLimitState(
        var config: RateLimitConfig,
        var tokens: Double,
        var lastRefill: Long,
        var consecutiveErrors: Int = 0,
        var lastErrorTime: Long = 0,
        var lastRequestTime: Long = 0
    )
    
    override suspend fun acquire(domain: String, weight: Int) {
        val normalizedDomain = domain.normalizeDomain()
        
        while (true) {
            val waitTime = mutex.withLock {
                val state = getOrCreateState(normalizedDomain)
                refillTokens(state)
                
                if (state.tokens >= weight) {
                    state.tokens -= weight
                    state.lastRequestTime = currentTimeMillis()
                    return@withLock 0L
                }
                
                // Calculate wait time
                val tokensNeeded = weight - state.tokens
                val waitMs = (tokensNeeded / state.config.requestsPerSecond * 1000).toLong()
                minOf(waitMs, state.config.maxDelayMs)
            }
            
            if (waitTime == 0L) return
            delay(waitTime)
        }
    }
    
    override fun tryAcquire(domain: String, weight: Int): Boolean {
        val normalizedDomain = domain.normalizeDomain()
        val state = states[normalizedDomain] ?: return true
        
        refillTokens(state)
        
        return if (state.tokens >= weight) {
            state.tokens -= weight
            state.lastRequestTime = currentTimeMillis()
            true
        } else {
            false
        }
    }

    override fun setLimit(domain: String, config: RateLimitConfig) {
        val normalizedDomain = domain.normalizeDomain()
        val state = getOrCreateState(normalizedDomain)
        state.config = config
    }
    
    override fun getStats(domain: String): RateLimitStats {
        val normalizedDomain = domain.normalizeDomain()
        val state = states[normalizedDomain]
        
        return if (state != null) {
            refillTokens(state)
            RateLimitStats(
                domain = normalizedDomain,
                requestsPerSecond = state.config.requestsPerSecond,
                availableTokens = state.tokens,
                consecutiveErrors = state.consecutiveErrors,
                lastRequestTime = state.lastRequestTime,
                isThrottled = state.tokens < 1
            )
        } else {
            RateLimitStats(
                domain = normalizedDomain,
                requestsPerSecond = defaultConfig.requestsPerSecond,
                availableTokens = defaultConfig.burstSize.toDouble(),
                consecutiveErrors = 0,
                lastRequestTime = 0,
                isThrottled = false
            )
        }
    }
    
    override fun onResponse(domain: String, statusCode: Int, retryAfter: Long?) {
        val normalizedDomain = domain.normalizeDomain()
        val state = getOrCreateState(normalizedDomain)
        
        if (!state.config.adaptiveEnabled) return
        
        when (statusCode) {
            429 -> {
                // Rate limited - back off significantly
                state.consecutiveErrors++
                state.lastErrorTime = currentTimeMillis()
                
                // Reduce rate by 50%
                val newRate = state.config.requestsPerSecond * 0.5
                state.config = state.config.copy(
                    requestsPerSecond = maxOf(newRate, 0.1)
                )
                
                // If retry-after header present, respect it
                if (retryAfter != null && retryAfter > 0) {
                    state.tokens = -retryAfter.toDouble()
                }
            }
            in 200..299 -> {
                // Success - gradually recover rate
                if (state.consecutiveErrors > 0) {
                    state.consecutiveErrors = 0
                    
                    // Slowly increase rate (10% per success)
                    val newRate = state.config.requestsPerSecond * 1.1
                    state.config = state.config.copy(
                        requestsPerSecond = minOf(newRate, defaultConfig.requestsPerSecond)
                    )
                }
            }
            503 -> {
                // Server overloaded - moderate backoff
                state.consecutiveErrors++
                state.config = state.config.copy(
                    requestsPerSecond = state.config.requestsPerSecond * 0.7
                )
            }
            in 500..599 -> {
                // Other server errors - slight backoff
                state.consecutiveErrors++
                state.config = state.config.copy(
                    requestsPerSecond = state.config.requestsPerSecond * 0.9
                )
            }
        }
    }

    override fun reset(domain: String) {
        states.remove(domain.normalizeDomain())
    }
    
    override fun resetAll() {
        states.clear()
    }
    
    private fun getOrCreateState(domain: String): RateLimitState {
        return states.getOrPut(domain) {
            RateLimitState(
                config = defaultConfig.copy(),
                tokens = defaultConfig.burstSize.toDouble(),
                lastRefill = currentTimeMillis()
            )
        }
    }
    
    private fun refillTokens(state: RateLimitState) {
        val now = currentTimeMillis()
        val elapsed = now - state.lastRefill
        
        if (elapsed > 0) {
            val tokensToAdd = elapsed * state.config.requestsPerSecond / 1000.0
            state.tokens = minOf(
                state.tokens + tokensToAdd,
                state.config.burstSize.toDouble()
            )
            state.lastRefill = now
        }
    }
    
    private fun String.normalizeDomain(): String {
        return this.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }
}

/**
 * Simple rate limiter with fixed delay between requests
 */
class SimpleRateLimiter(
    private val delayMs: Long = 500
) : RateLimiter {
    
    private val lastRequestTimes = mutableMapOf<String, Long>()
    
    override suspend fun acquire(domain: String, weight: Int) {
        val normalizedDomain = domain.normalizeDomain()
        val lastTime = lastRequestTimes[normalizedDomain] ?: 0
        val elapsed = currentTimeMillis() - lastTime
        val waitTime = (delayMs * weight) - elapsed
        
        if (waitTime > 0) {
            delay(waitTime)
        }
        
        lastRequestTimes[normalizedDomain] = currentTimeMillis()
    }
    
    override fun tryAcquire(domain: String, weight: Int): Boolean {
        val normalizedDomain = domain.normalizeDomain()
        val lastTime = lastRequestTimes[normalizedDomain] ?: 0
        val elapsed = currentTimeMillis() - lastTime
        
        return if (elapsed >= delayMs * weight) {
            lastRequestTimes[normalizedDomain] = currentTimeMillis()
            true
        } else {
            false
        }
    }
    
    override fun setLimit(domain: String, config: RateLimitConfig) {
        // Not supported in simple rate limiter
    }
    
    override fun getStats(domain: String): RateLimitStats {
        val normalizedDomain = domain.normalizeDomain()
        return RateLimitStats(
            domain = normalizedDomain,
            requestsPerSecond = 1000.0 / delayMs,
            availableTokens = 1.0,
            consecutiveErrors = 0,
            lastRequestTime = lastRequestTimes[normalizedDomain] ?: 0,
            isThrottled = false
        )
    }
    
    override fun onResponse(domain: String, statusCode: Int, retryAfter: Long?) {
        // Not adaptive
    }
    
    override fun reset(domain: String) {
        lastRequestTimes.remove(domain.normalizeDomain())
    }
    
    override fun resetAll() {
        lastRequestTimes.clear()
    }
    
    private fun String.normalizeDomain(): String {
        return this.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }
}
