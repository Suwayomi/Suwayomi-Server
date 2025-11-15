package ireader.core.http

import ireader.core.prefs.PreferenceStore
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class PersistentCookieStore(
    private val preferenceStore: PreferenceStore,
) {
    private val cookieMap = ConcurrentHashMap<String, List<Cookie>>()

    val keyPref = preferenceStore.getString("cookie_manager_keys")
    val keys = mutableListOf<String>()
    val cookieSeparator = "#COOKIE_SEPARATOR#"

    fun decodePrefsCookies() {
        keys.addAll(keyPref.get().split(cookieSeparator))
    }

    init {
        decodePrefsCookies()
        val cookies =
            keys.map {
                it to preferenceStore.getString(it).get()
            }
        for ((key, value) in cookies) {
            @Suppress("UNCHECKED_CAST")
            val cookies = value as? Set<String>
            if (cookies != null) {
                try {
                    val url = "http://$key".toHttpUrlOrNull() ?: continue
                    val nonExpiredCookies =
                        cookies
                            .mapNotNull { Cookie.parse(url, it) }
                            .filter { !it.hasExpired() }
                    cookieMap.put(key, nonExpiredCookies)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    @Synchronized
    fun addAll(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        val key = url.toUri().host

        // Append or replace the cookies for this domain.
        val cookiesForDomain = cookieMap[key].orEmpty().toMutableList()
        for (cookie in cookies) {
            // Find a cookie with the same name. Replace it if found, otherwise add a new one.
            val pos = cookiesForDomain.indexOfFirst { it.name == cookie.name }
            if (pos == -1) {
                cookiesForDomain.add(cookie)
            } else {
                cookiesForDomain[pos] = cookie
            }
        }
        cookieMap.put(key, cookiesForDomain)

        // Get cookies to be stored in disk
        val newValues =
            cookiesForDomain
                .asSequence()
                .filter { it.persistent && !it.hasExpired() }
                .map(Cookie::toString)
                .toSet()

        keyPref.set(keys.joinToString(cookieSeparator))
        preferenceStore.getStringSet(key).set(newValues)
    }

    @Synchronized
    fun removeAll() {
        cookieMap.clear()
    }

    fun remove(uri: URI) {
        preferenceStore.getStringSet(uri.host).delete()
        cookieMap.remove(uri.host)
    }

    fun get(url: HttpUrl) = get(url.toUri().host)

    fun get(uri: URI) = get(uri.host)

    private fun get(url: String): List<Cookie> = cookieMap[url].orEmpty().filter { !it.hasExpired() }

    private fun Cookie.hasExpired() = System.currentTimeMillis() >= expiresAt
}
