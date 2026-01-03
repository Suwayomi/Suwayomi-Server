package ireader.core.http

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Platform-agnostic Cookie representation.
 * Replaces okhttp3.Cookie for KMP compatibility.
 */
@Serializable
data class Cookie(
    val name: String,
    val value: String,
    val domain: String = "",
    val path: String = "/",
    val expiresAt: Long = 0L,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val persistent: Boolean = false
) {
    /**
     * Check if cookie has expired
     */
    @OptIn(ExperimentalTime::class)
    fun isExpired(): Boolean {
        return expiresAt != 0L && expiresAt < Clock.System.now().toEpochMilliseconds()
    }
    
    /**
     * Convert to cookie header string format
     */
    fun toHeaderString(): String = "$name=$value"
    
    companion object {
        /**
         * Parse a Set-Cookie header value
         */
        @OptIn(ExperimentalTime::class)
        fun parse(url: String, setCookieHeader: String): Cookie? {
            val parts = setCookieHeader.split(";").map { it.trim() }
            if (parts.isEmpty()) return null
            
            val nameValue = parts[0].split("=", limit = 2)
            if (nameValue.size != 2) return null
            
            val name = nameValue[0].trim()
            val value = nameValue[1].trim()
            
            var domain = ""
            var path = "/"
            var expiresAt = 0L
            var secure = false
            var httpOnly = false
            
            for (i in 1 until parts.size) {
                val part = parts[i].lowercase()
                when {
                    part.startsWith("domain=") -> domain = parts[i].substringAfter("=").trim()
                    part.startsWith("path=") -> path = parts[i].substringAfter("=").trim()
                    part.startsWith("max-age=") -> {
                        val maxAge = parts[i].substringAfter("=").trim().toLongOrNull() ?: 0L
                        expiresAt = Clock.System.now().toEpochMilliseconds() + (maxAge * 1000)
                    }
                    part == "secure" -> secure = true
                    part == "httponly" -> httpOnly = true
                }
            }
            
            // Extract domain from URL if not specified
            if (domain.isEmpty()) {
                domain = try {
                    url.substringAfter("://").substringBefore("/").substringBefore(":")
                } catch (e: Exception) {
                    ""
                }
            }
            
            return Cookie(
                name = name,
                value = value,
                domain = domain,
                path = path,
                expiresAt = expiresAt,
                secure = secure,
                httpOnly = httpOnly,
                persistent = expiresAt != 0L
            )
        }
        
        /**
         * Parse multiple cookies from response headers
         */
        fun parseAll(url: String, setCookieHeaders: List<String>): List<Cookie> {
            return setCookieHeaders.mapNotNull { parse(url, it) }
        }
    }
}

/**
 * Type alias for headers - replaces okhttp3.Headers
 */
typealias Headers = Map<String, String>

/**
 * Helper to build headers
 */
fun headersOf(vararg pairs: Pair<String, String>): Headers = mapOf(*pairs)
