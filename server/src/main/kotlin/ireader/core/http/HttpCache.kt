package ireader.core.http

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.http.Headers as KtorHeaders

/**
 * Cache entry for HTTP responses
 */
data class CacheEntry(
    val response: ByteArray,
    val contentType: ContentType?,
    val headers: KtorHeaders,
    val statusCode: HttpStatusCode,
    val expiresAt: Long
) {
    fun isExpired(): Boolean = getTimeMillis() > expiresAt
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CacheEntry
        if (!response.contentEquals(other.response)) return false
        if (contentType != other.contentType) return false
        if (headers != other.headers) return false
        if (statusCode != other.statusCode) return false
        if (expiresAt != other.expiresAt) return false
        return true
    }
    
    override fun hashCode(): Int {
        var result = response.contentHashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + headers.hashCode()
        result = 31 * result + statusCode.hashCode()
        result = 31 * result + expiresAt.hashCode()
        return result
    }
}

/**
 * Simple in-memory HTTP cache with time-based expiration
 */
class HttpCache(
    private val defaultCacheDurationMs: Long = 5 * 60 * 1000 // 5 minutes default
) {
    private val cache = mutableMapOf<String, CacheEntry>()
    private val mutex = Mutex()
    
    suspend fun get(key: String): CacheEntry? = mutex.withLock {
        val entry = cache[key]
        if (entry != null && entry.isExpired()) {
            cache.remove(key)
            return@withLock null
        }
        entry
    }
    
    suspend fun put(key: String, entry: CacheEntry) = mutex.withLock {
        cache[key] = entry
    }
    
    suspend fun clear() = mutex.withLock {
        cache.clear()
    }
    
    suspend fun remove(key: String) = mutex.withLock {
        cache.remove(key)
    }
    
    suspend fun size(): Int = mutex.withLock {
        cache.size
    }
    
    /**
     * Remove all expired entries
     */
    suspend fun cleanExpired() = mutex.withLock {
        val keysToRemove = cache.filter { it.value.isExpired() }.keys
        keysToRemove.forEach { cache.remove(it) }
    }
    
    /**
     * Generate cache key from URL and method
     */
    fun generateKey(url: String, method: HttpMethod): String {
        return "${method.value}:$url"
    }
}
