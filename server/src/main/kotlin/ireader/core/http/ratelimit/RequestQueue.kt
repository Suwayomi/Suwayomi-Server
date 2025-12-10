package ireader.core.http.ratelimit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ireader.core.log.Log

/**
 * Priority-based request queue for managing concurrent requests
 */
interface RequestQueue {
    /**
     * Enqueue a request and wait for its result
     */
    suspend fun <T> enqueue(
        domain: String,
        priority: Priority = Priority.NORMAL,
        request: suspend () -> T
    ): T
    
    /**
     * Cancel all pending requests for a domain
     */
    fun cancel(domain: String)
    
    /**
     * Cancel all pending requests
     */
    fun cancelAll()
    
    /**
     * Get the number of pending requests for a domain
     */
    fun getQueueSize(domain: String): Int
    
    /**
     * Get total number of pending requests
     */
    fun getTotalQueueSize(): Int
    
    /**
     * Check if queue is processing
     */
    fun isProcessing(domain: String): Boolean
}

enum class Priority(val value: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3)
}

/**
 * Request queue configuration
 */
data class RequestQueueConfig(
    /** Maximum concurrent requests per domain */
    val maxConcurrentPerDomain: Int = 2,
    /** Maximum total concurrent requests */
    val maxConcurrentTotal: Int = 10,
    /** Maximum queue size per domain */
    val maxQueueSizePerDomain: Int = 50,
    /** Request timeout in milliseconds */
    val requestTimeoutMs: Long = 60000
)


/**
 * Default implementation of RequestQueue
 */
class DefaultRequestQueue(
    private val config: RequestQueueConfig = RequestQueueConfig(),
    private val rateLimiter: RateLimiter? = null
) : RequestQueue {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    
    private data class QueuedRequest<T>(
        val domain: String,
        val priority: Priority,
        val request: suspend () -> T,
        val result: CompletableDeferred<T>
    )
    
    private val queues = mutableMapOf<String, MutableList<QueuedRequest<*>>>()
    private val activeRequests = mutableMapOf<String, Int>()
    private val processing = mutableSetOf<String>()
    
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> enqueue(
        domain: String,
        priority: Priority,
        request: suspend () -> T
    ): T {
        val normalizedDomain = domain.normalizeDomain()
        val deferred = CompletableDeferred<T>()
        
        val queuedRequest = QueuedRequest(
            domain = normalizedDomain,
            priority = priority,
            request = request,
            result = deferred
        )
        
        mutex.withLock {
            val queue = queues.getOrPut(normalizedDomain) { mutableListOf() }
            
            // Check queue size limit
            if (queue.size >= config.maxQueueSizePerDomain) {
                throw QueueFullException("Queue full for domain: $normalizedDomain")
            }
            
            // Insert based on priority (higher priority first)
            val insertIndex = queue.indexOfFirst { it.priority.value < priority.value }
            if (insertIndex == -1) {
                queue.add(queuedRequest as QueuedRequest<*>)
            } else {
                queue.add(insertIndex, queuedRequest as QueuedRequest<*>)
            }
        }
        
        // Start processing if not already
        processQueue(normalizedDomain)
        
        return deferred.await()
    }
    
    override fun cancel(domain: String) {
        val normalizedDomain = domain.normalizeDomain()
        scope.launch {
            mutex.withLock {
                val queue = queues[normalizedDomain] ?: return@withLock
                queue.forEach { request ->
                    request.result.cancel()
                }
                queue.clear()
            }
        }
    }
    
    override fun cancelAll() {
        scope.launch {
            mutex.withLock {
                queues.values.flatten().forEach { request ->
                    request.result.cancel()
                }
                queues.clear()
            }
        }
    }
    
    override fun getQueueSize(domain: String): Int {
        return queues[domain.normalizeDomain()]?.size ?: 0
    }
    
    override fun getTotalQueueSize(): Int {
        return queues.values.sumOf { it.size }
    }
    
    override fun isProcessing(domain: String): Boolean {
        return processing.contains(domain.normalizeDomain())
    }

    @Suppress("UNCHECKED_CAST")
    private fun processQueue(domain: String) {
        scope.launch {
            if (processing.contains(domain)) return@launch
            
            mutex.withLock {
                if (processing.contains(domain)) return@withLock
                processing.add(domain)
            }
            
            try {
                while (true) {
                    val request = mutex.withLock {
                        val queue = queues[domain] ?: return@withLock null
                        if (queue.isEmpty()) {
                            queues.remove(domain)
                            return@withLock null
                        }
                        
                        val currentActive = activeRequests[domain] ?: 0
                        if (currentActive >= config.maxConcurrentPerDomain) {
                            return@withLock null
                        }
                        
                        activeRequests[domain] = currentActive + 1
                        queue.removeAt(0)
                    } ?: break
                    
                    // Apply rate limiting if configured
                    rateLimiter?.acquire(domain)
                    
                    // Execute the request
                    try {
                        val result = (request as QueuedRequest<Any?>).request()
                        request.result.complete(result)
                    } catch (e: Exception) {
                        request.result.completeExceptionally(e)
                    } finally {
                        mutex.withLock {
                            val current = activeRequests[domain] ?: 1
                            if (current <= 1) {
                                activeRequests.remove(domain)
                            } else {
                                activeRequests[domain] = current - 1
                            }
                        }
                    }
                }
            } finally {
                mutex.withLock {
                    processing.remove(domain)
                }
            }
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

class QueueFullException(message: String) : Exception(message)
