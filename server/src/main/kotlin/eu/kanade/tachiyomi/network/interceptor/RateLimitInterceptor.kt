package eu.kanade.tachiyomi.network.interceptor

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

/**
 * An OkHttp interceptor that handles rate limiting.
 *
 * This uses `java.time` APIs and is the legacy method, kept
 * for compatibility reasons with existing extensions.
 *
 * Examples:
 *
 * permits = 5,  period = 1, unit = seconds  =>  5 requests per second
 * permits = 10, period = 2, unit = minutes  =>  10 requests per 2 minutes
 *
 * @since extension-lib 1.3
 *
 * @param permits [Int]   Number of requests allowed within a period of units.
 * @param period [Long]   The limiting duration. Defaults to 1.
 * @param unit [TimeUnit] The unit of time for the period. Defaults to seconds.
 */
@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
) = addInterceptor(RateLimitInterceptor(null, permits, period.toDuration(unit.toDurationUnit())))

/**
 * An OkHttp interceptor that handles rate limiting.
 *
 * Examples:
 *
 * permits = 5,  period = 1.seconds  =>  5 requests per second
 * permits = 10, period = 2.minutes  =>  10 requests per 2 minutes
 *
 * @since extension-lib 1.5
 *
 * @param permits [Int]     Number of requests allowed within a period of units.
 * @param period [Duration] The limiting duration. Defaults to 1.seconds.
 */
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
) = addInterceptor(RateLimitInterceptor(null, permits, period))

/** We can probably accept domains or wildcards by comparing with [endsWith], etc. */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal class RateLimitInterceptor(
    private val host: String?,
    private val permits: Int,
    period: Duration,
) : Interceptor {
    init {
        // permits <= 0 made every request crash on requestQueue.first inside the
        // wait loop; a non-positive period silently disabled limiting.
        require(permits > 0) { "permits must be positive, got $permits" }
        require(period.isPositive()) { "period must be positive, got $period" }
    }

    /**
     * Identity token for a queued request. Two requests can land on the same
     * [timestamp] millisecond — value-keyed removal would then evict the wrong
     * slot, so removal matches the entry object itself.
     */
    private class QueueEntry(
        val timestamp: Long,
        val sequence: Long,
    )

    private val requestQueue = ArrayDeque<QueueEntry>(permits)
    private val sequenceGenerator = AtomicLong()
    private val rateLimitMillis = period.inWholeMilliseconds
    private val fairLock = Semaphore(1, true)

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        if (call.isCanceled()) throw IOException("Canceled")

        val request = chain.request()
        when (host) {
            // need rate limit
            null, request.url.host -> {}

            else -> {
                return chain.proceed(request)
            }
        }

        try {
            fairLock.acquire()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e)
        }

        val requestQueue = this.requestQueue
        val entry: QueueEntry

        try {
            synchronized(requestQueue) {
                while (requestQueue.size >= permits) { // queue is full, remove expired entries
                    val periodStart = SystemClock.elapsedRealtime() - rateLimitMillis
                    var hasRemovedExpired = false
                    while (requestQueue.isEmpty().not() && requestQueue.first.timestamp <= periodStart) {
                        requestQueue.removeFirst()
                        hasRemovedExpired = true
                    }
                    if (call.isCanceled()) {
                        throw IOException("Canceled")
                    } else if (hasRemovedExpired) {
                        break
                    } else {
                        try {
                            // wait for the first entry to expire, or notified by cached response
                            (requestQueue as Object).wait(requestQueue.first.timestamp - periodStart)
                        } catch (_: InterruptedException) {
                            // restore the interrupt flag and bail out — spinning on
                            // wait() with a set interrupt flag would busy-loop
                            Thread.currentThread().interrupt()
                            if (call.isCanceled()) throw IOException("Canceled")
                            break
                        }
                    }
                }

                // add request to queue
                entry = QueueEntry(SystemClock.elapsedRealtime(), sequenceGenerator.incrementAndGet())
                requestQueue.addLast(entry)
            }
        } finally {
            fairLock.release()
        }

        val response = chain.proceed(request)
        if (response.networkResponse == null) { // response is cached, remove it from queue
            synchronized(requestQueue) {
                if (requestQueue.isEmpty() || entry.timestamp < requestQueue.first.timestamp) return@synchronized
                requestQueue.remove(entry)
                (requestQueue as Object).notifyAll()
            }
        }

        return response
    }
}
