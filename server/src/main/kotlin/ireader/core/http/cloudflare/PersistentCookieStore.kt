package ireader.core.http.cloudflare

import ireader.core.prefs.PreferenceStore
import ireader.core.util.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent implementation of CloudflareCookieStore using PreferenceStore
 */
class PersistentCloudfareCookieStore(
    private val preferences: PreferenceStore
) : CloudflareCookieStore {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val memoryCache = mutableMapOf<String, ClearanceCookie>()
    private var cacheLoaded = false
    
    companion object {
        private const val PREF_KEY_PREFIX = "cf_cookie_"
        private const val PREF_KEY_DOMAINS = "cf_cookie_domains"
    }
    
    override suspend fun getClearanceCookie(domain: String): ClearanceCookie? {
        ensureCacheLoaded()
        val normalizedDomain = domain.normalizeDomain()
        val cookie = memoryCache[normalizedDomain]
        
        // Check if expired
        if (cookie != null && cookie.isExpired()) {
            invalidate(normalizedDomain)
            return null
        }
        
        return cookie
    }
    
    override suspend fun saveClearanceCookie(domain: String, cookie: ClearanceCookie) {
        val normalizedDomain = domain.normalizeDomain()
        memoryCache[normalizedDomain] = cookie
        
        // Persist to preferences
        try {
            val serializable = cookie.toSerializable()
            val jsonString = json.encodeToString(serializable)
            preferences.getString("$PREF_KEY_PREFIX$normalizedDomain", "").set(jsonString)
            
            // Update domain list
            val domains = getDomainList().toMutableSet()
            domains.add(normalizedDomain)
            preferences.getString(PREF_KEY_DOMAINS, "").set(domains.joinToString(","))
        } catch (e: Exception) {
            // Log but don't fail - memory cache still works
        }
    }

    override suspend fun isValid(cookie: ClearanceCookie): Boolean {
        return !cookie.isExpired() && cookie.cfClearance.isNotBlank()
    }
    
    override suspend fun invalidate(domain: String) {
        val normalizedDomain = domain.normalizeDomain()
        memoryCache.remove(normalizedDomain)
        
        try {
            preferences.getString("$PREF_KEY_PREFIX$normalizedDomain", "").delete()
            
            // Update domain list
            val domains = getDomainList().toMutableSet()
            domains.remove(normalizedDomain)
            preferences.getString(PREF_KEY_DOMAINS, "").set(domains.joinToString(","))
        } catch (e: Exception) {
            // Ignore persistence errors
        }
    }
    
    override suspend fun getAll(): List<ClearanceCookie> {
        ensureCacheLoaded()
        return memoryCache.values.filter { !it.isExpired() }
    }
    
    override suspend fun clearAll() {
        memoryCache.clear()
        
        try {
            val domains = getDomainList()
            domains.forEach { domain ->
                preferences.getString("$PREF_KEY_PREFIX$domain", "").delete()
            }
            preferences.getString(PREF_KEY_DOMAINS, "").delete()
        } catch (e: Exception) {
            // Ignore persistence errors
        }
    }
    
    private fun ensureCacheLoaded() {
        if (cacheLoaded) return
        
        try {
            val domains = getDomainList()
            domains.forEach { domain ->
                val jsonString = preferences.getString("$PREF_KEY_PREFIX$domain", "").get()
                if (jsonString.isNotBlank()) {
                    try {
                        val serializable = json.decodeFromString<SerializableClearanceCookie>(jsonString)
                        val cookie = serializable.toClearanceCookie()
                        if (!cookie.isExpired()) {
                            memoryCache[domain] = cookie
                        }
                    } catch (e: Exception) {
                        // Invalid cookie data, skip
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore load errors
        }
        
        cacheLoaded = true
    }
    
    private fun getDomainList(): List<String> {
        val domainsString = preferences.getString(PREF_KEY_DOMAINS, "").get()
        return if (domainsString.isBlank()) {
            emptyList()
        } else {
            domainsString.split(",").filter { it.isNotBlank() }
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
 * Serializable version of ClearanceCookie for JSON persistence
 */
@Serializable
private data class SerializableClearanceCookie(
    val cfClearance: String,
    val cfBm: String? = null,
    val userAgent: String,
    val timestamp: Long,
    val expiresAt: Long,
    val domain: String
) {
    fun toClearanceCookie(): ClearanceCookie = ClearanceCookie(
        cfClearance = cfClearance,
        cfBm = cfBm,
        userAgent = userAgent,
        timestamp = timestamp,
        expiresAt = expiresAt,
        domain = domain
    )
}

private fun ClearanceCookie.toSerializable(): SerializableClearanceCookie = SerializableClearanceCookie(
    cfClearance = cfClearance,
    cfBm = cfBm,
    userAgent = userAgent,
    timestamp = timestamp,
    expiresAt = expiresAt,
    domain = domain
)
