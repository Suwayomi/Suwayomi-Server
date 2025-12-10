package ireader.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ExperimentalTime

/**
 * Rate limiter for controlling request frequency to sources.
 * Prevents overwhelming servers and getting blocked.
 */
class RateLimiter(
    private val permits: Int = 1,
    private val periodMillis: Long = 1000
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>(permits)

    /**
     * Acquires a permit, waiting if necessary.
     * Suspends until a permit is available.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun acquire() {
        mutex.withLock {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()

            // Remove old timestamps outside the time window
            while (timestamps.isNotEmpty() && now - timestamps.first() >= periodMillis) {
                timestamps.removeFirst()
            }

            // If we've hit the limit, wait
            if (timestamps.size >= permits) {
                val oldestTimestamp = timestamps.first()
                val waitTime = periodMillis - (now - oldestTimestamp)
                if (waitTime > 0) {
                    delay(waitTime)
                }
                // Remove the oldest after waiting
                timestamps.removeFirst()
            }

            // Add current timestamp
            timestamps.addLast(kotlin.time.Clock.System.now().toEpochMilliseconds())
        }
    }

    /**
     * Executes a block with rate limiting.
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        acquire()
        return block()
    }
}

/**
 * Global rate limiter manager for managing per-source rate limits.
 */
object RateLimiterManager {
    private val limiters = mutableMapOf<String, RateLimiter>()
    private val mutex = Mutex()

    /**
     * Gets or creates a rate limiter for a source.
     *
     * @param sourceId Unique identifier for the source
     * @param permits Number of permits per period
     * @param periodMillis Time period in milliseconds
     */
    suspend fun getOrCreate(
        sourceId: String,
        permits: Int = 2,
        periodMillis: Long = 1000
    ): RateLimiter {
        return mutex.withLock {
            limiters.getOrPut(sourceId) {
                RateLimiter(permits, periodMillis)
            }
        }
    }

    /**
     * Removes a rate limiter for a source.
     */
    suspend fun remove(sourceId: String) {
        mutex.withLock {
            limiters.remove(sourceId)
        }
    }

    /**
     * Clears all rate limiters.
     */
    suspend fun clear() {
        mutex.withLock {
            limiters.clear()
        }
    }
}
