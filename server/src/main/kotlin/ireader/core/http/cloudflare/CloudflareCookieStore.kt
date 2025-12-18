package ireader.core.http.cloudflare

import ireader.core.util.currentTimeMillis

/**
 * Stores and manages Cloudflare clearance cookies for reuse
 */
interface CloudflareCookieStore {
    /**
     * Get stored clearance cookie for a domain
     */
    suspend fun getClearanceCookie(domain: String): ClearanceCookie?
    
    /**
     * Save clearance cookie for a domain
     */
    suspend fun saveClearanceCookie(domain: String, cookie: ClearanceCookie)
    
    /**
     * Check if a cookie is still valid
     */
    suspend fun isValid(cookie: ClearanceCookie): Boolean
    
    /**
     * Invalidate cookie for a domain
     */
    suspend fun invalidate(domain: String)
    
    /**
     * Get all stored cookies
     */
    suspend fun getAll(): List<ClearanceCookie>
    
    /**
     * Clear all stored cookies
     */
    suspend fun clearAll()
}

/**
 * Cloudflare clearance cookie data
 */
data class ClearanceCookie(
    val cfClearance: String,
    val cfBm: String? = null,
    val userAgent: String,
    val timestamp: Long,
    val expiresAt: Long,
    val domain: String
) {
    /**
     * Check if cookie has expired
     */
    fun isExpired(): Boolean = currentTimeMillis() > expiresAt
    
    /**
     * Get remaining validity time in milliseconds
     */
    fun remainingValidityMs(): Long = maxOf(0, expiresAt - currentTimeMillis())
    
    /**
     * Check if cookie will expire soon (within 5 minutes)
     */
    fun isExpiringSoon(): Boolean = remainingValidityMs() < 5 * 60 * 1000

    companion object {
        /** Default cookie validity: 30 minutes */
        const val DEFAULT_VALIDITY_MS = 30 * 60 * 1000L
        
        /** Maximum cookie validity: 2 hours */
        const val MAX_VALIDITY_MS = 2 * 60 * 60 * 1000L
    }
}

/**
 * In-memory implementation of CloudflareCookieStore
 * For production, use a persistent implementation
 */
class InMemoryCloudfareCookieStore : CloudflareCookieStore {
    
    private val cookies = mutableMapOf<String, ClearanceCookie>()
    
    override suspend fun getClearanceCookie(domain: String): ClearanceCookie? {
        val cookie = cookies[domain.normalizeDomain()]
        return if (cookie != null && !cookie.isExpired()) cookie else null
    }
    
    override suspend fun saveClearanceCookie(domain: String, cookie: ClearanceCookie) {
        cookies[domain.normalizeDomain()] = cookie
    }
    
    override suspend fun isValid(cookie: ClearanceCookie): Boolean {
        return !cookie.isExpired() && cookie.cfClearance.isNotBlank()
    }
    
    override suspend fun invalidate(domain: String) {
        cookies.remove(domain.normalizeDomain())
    }
    
    override suspend fun getAll(): List<ClearanceCookie> {
        return cookies.values.filter { !it.isExpired() }
    }
    
    override suspend fun clearAll() {
        cookies.clear()
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
