package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.withLock
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// from TachiWeb-Server
class PersistentCookieStore(
    context: Context,
) : CookieStore {
    private val cookieMap = ConcurrentHashMap<String, List<Cookie>>()
    private val prefs = context.getSharedPreferences("cookie_store", Context.MODE_PRIVATE)

    private val lock = ReentrantLock()

    init {
        val domains =
            prefs.all.keys
                .map { it.substringBeforeLast(".") }
                .toSet()
        domains.forEach { domain ->
            val cookies = prefs.getStringSet(domain, emptySet())
            if (!cookies.isNullOrEmpty()) {
                try {
                    val url = "http://$domain".toHttpUrlOrNull() ?: return@forEach
                    val nonExpiredCookies =
                        cookies
                            .mapNotNull { Cookie.parse(url, it) }
                            .filter { !it.hasExpired() }
                    cookieMap[domain] = nonExpiredCookies
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun addAll(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        lock.withLock {
            // Append or replace the cookies for this domain.
            val cookiesForDomain = cookieMap[url.host].orEmpty().toMutableList()
            for (cookie in cookies) {
                // Find a cookie with the same name. Replace it if found, otherwise add a new one.
                val pos = cookiesForDomain.indexOfFirst { it.name == cookie.name }
                if (pos == -1) {
                    cookiesForDomain.add(cookie)
                } else {
                    cookiesForDomain[pos] = cookie
                }
            }
            cookieMap[url.host] = cookiesForDomain

            saveToDisk(url.toUrl())
        }
    }

    override fun removeAll(): Boolean =
        lock.withLock {
            val wasNotEmpty = cookieMap.isEmpty()
            prefs.edit().clear().apply()
            cookieMap.clear()
            wasNotEmpty
        }

    fun remove(uri: URI) {
        val url = uri.toURL()
        lock.withLock {
            prefs.edit().remove(url.host).apply()
            cookieMap.remove(url.host)
        }
    }

    override fun get(uri: URI): List<HttpCookie> {
        val url = uri.toURL()
        return get(url.host).map {
            it.toHttpCookie()
        }
    }

    fun get(url: HttpUrl): List<Cookie> = get(url.host)

    override fun add(
        uri: URI?,
        cookie: HttpCookie,
    ) {
        @Suppress("NAME_SHADOWING")
        val uri = uri ?: URI("http://" + cookie.domain.removePrefix("."))
        val url = uri.toURL()
        lock.withLock {
            val cookies = cookieMap[url.host]
            cookieMap[url.host] = cookies.orEmpty() + cookie.toCookie(uri)
            saveToDisk(url)
        }
    }

    override fun getCookies(): List<HttpCookie> =
        cookieMap.values.flatMap {
            it.map {
                it.toHttpCookie()
            }
        }

    override fun getURIs(): List<URI> =
        cookieMap.keys().toList().map {
            URI("http://$it")
        }

    override fun remove(
        uri: URI?,
        cookie: HttpCookie,
    ): Boolean {
        @Suppress("NAME_SHADOWING")
        val uri = uri ?: URI("http://" + cookie.domain.removePrefix("."))
        val url = uri.toURL()
        return lock.withLock {
            val cookies = cookieMap[url.host].orEmpty()
            val index =
                cookies.indexOfFirst {
                    it.name == cookie.name &&
                        it.path == cookie.path
                }
            if (index >= 0) {
                val newList = cookies.toMutableList()
                newList.removeAt(index)
                cookieMap[url.host] = newList.toList()
                saveToDisk(url)
                true
            } else {
                false
            }
        }
    }

    private fun get(url: String): List<Cookie> = cookieMap[url].orEmpty().filter { !it.hasExpired() }

    private fun saveToDisk(url: URL) {
        // Get cookies to be stored in disk
        val newValues =
            cookieMap[url.host]
                .orEmpty()
                .asSequence()
                .filter { it.persistent && !it.hasExpired() }
                .map(Cookie::toString)
                .toSet()

        prefs.edit().putStringSet(url.host, newValues).apply()
    }

    private fun Cookie.hasExpired() = System.currentTimeMillis() >= expiresAt

    private fun HttpCookie.toCookie(uri: URI) =
        Cookie
            .Builder()
            .name(name)
            .value(value)
            .domain(uri.toURL().host)
            .path(path ?: "/")
            .let {
                if (maxAge != -1L) {
                    it.expiresAt(System.currentTimeMillis() + maxAge.seconds.inWholeMilliseconds)
                } else {
                    it.expiresAt(Long.MAX_VALUE)
                }
            }.let {
                if (secure) {
                    it.secure()
                } else {
                    it
                }
            }.let {
                if (isHttpOnly) {
                    it.httpOnly()
                } else {
                    it
                }
            }.build()

    private fun Cookie.toHttpCookie(): HttpCookie {
        val it = this
        return HttpCookie(it.name, it.value).apply {
            domain = it.domain
            path = it.path
            secure = it.secure
            maxAge =
                if (it.persistent) {
                    -1
                } else {
                    (it.expiresAt.milliseconds - System.currentTimeMillis().milliseconds).inWholeSeconds
                }

            isHttpOnly = it.httpOnly
        }
    }
}
