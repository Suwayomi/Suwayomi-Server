package ireader.core.http

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ExperimentalTime

/**
 * Thread-safe cookie store for persisting cookies across sessions.
 * KMP-compatible implementation using common Cookie type.
 */
class PersistentCookieStore {
    private val cookies = mutableMapOf<String, MutableMap<String, Cookie>>()
    private val mutex = Mutex()
    
    suspend fun addCookies(url: String, newCookies: List<Cookie>) {
        val host = extractHost(url)
        mutex.withLock {
            val hostCookies = cookies.getOrPut(host) { mutableMapOf() }
            newCookies.forEach { cookie ->
                if (cookie.persistent || cookie.expiresAt == 0L) {
                    hostCookies[cookie.name] = cookie
                }
            }
        }
    }
    
    @OptIn(ExperimentalTime::class)
    suspend fun getCookies(url: String): List<Cookie> {
        val host = extractHost(url)
        return mutex.withLock {
            val hostCookies = cookies[host] ?: return@withLock emptyList()
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            
            val validCookies = hostCookies.values.filter { cookie ->
                cookie.expiresAt == 0L || cookie.expiresAt > now
            }
            
            hostCookies.entries.removeAll { (_, cookie) ->
                cookie.expiresAt != 0L && cookie.expiresAt <= now
            }
            
            validCookies
        }
    }
    
    suspend fun removeCookie(url: String, cookieName: String) {
        val host = extractHost(url)
        mutex.withLock {
            cookies[host]?.remove(cookieName)
        }
    }
    
    suspend fun clearCookies(url: String) {
        val host = extractHost(url)
        mutex.withLock {
            cookies.remove(host)
        }
    }
    
    suspend fun clearAll() {
        mutex.withLock {
            cookies.clear()
        }
    }
    
    suspend fun getAllCookies(): Map<String, List<Cookie>> {
        return mutex.withLock {
            cookies.mapValues { it.value.values.toList() }
        }
    }
    
    private fun extractHost(url: String): String {
        return try {
            url.substringAfter("://")
                .substringBefore("/")
                .substringBefore(":")
                .lowercase()
        } catch (e: Exception) {
            url
        }
    }
}
