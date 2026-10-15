package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import okio.withLock
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// from TachiWeb-Server
class PersistentCookieStore(
    context: Context,
) : CookieStore {
    private val cookieMap = mutableMapOf<String, List<Cookie>>()
    private val prefs = context.getSharedPreferences("cookie_store", Context.MODE_PRIVATE)
    private val publicSuffixDatabase = PublicSuffixDatabase.get()

    private val lock = ReentrantLock()

    init {
        lock.withLock {
            // `prefs.all` already returns *logical* keys: flattened set sub-keys
            // (`domain.0`, `domain.size`) are collapsed to their parent by
            // JavaSharedPreferences, so no last-segment stripping is needed here
            val domains = prefs.all.keys.toSet()
            val domainsToSave = mutableSetOf<String>()
            domains.forEach { domain ->
                // skip non-set entries rather than crashing init on a ClassCastException
                val cookies =
                    try {
                        prefs.getStringSet(domain, emptySet())
                    } catch (_: ClassCastException) {
                        null
                    }
                if (!cookies.isNullOrEmpty()) {
                    try {
                        val url = "http://$domain".toHttpUrlOrNull() ?: return@forEach
                        val nonExpiredCookies =
                            cookies
                                .mapNotNull { Cookie.parse(url, it) }
                                .filter { !it.hasExpired() }
                                .groupBy { it.domain }
                                .mapValues { it.value.distinctBy { it.name } }
                        nonExpiredCookies.forEach { (domain, cookies) ->
                            cookieMap[domain] = cookies
                            domainsToSave.add(domain)
                        }
                        domainsToSave.add(domain)
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
            }
            saveToDisk(domainsToSave)
        }
    }

    fun addAll(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        lock.withLock {
            val domainsToSave = mutableSetOf<String>()
            // Append or replace the cookies for this domain.
            for (cookie in cookies) {
                val cookiesForDomain = cookieMap[cookie.domain].orEmpty().toMutableList()
                val pos = cookiesForDomain.indexOfFirst { it.name == cookie.name }
                if (pos == -1) {
                    cookiesForDomain.add(cookie)
                } else {
                    cookiesForDomain[pos] = cookie
                }
                cookieMap[cookie.domain] = cookiesForDomain
                domainsToSave.add(cookie.domain)
            }

            saveToDisk(domainsToSave.toSet())
        }
    }

    override fun removeAll(): Boolean =
        lock.withLock {
            val wasNotEmpty = cookieMap.isNotEmpty()
            prefs.edit().clear().apply()
            cookieMap.clear()
            wasNotEmpty
        }

    fun remove(uri: URI) {
        // cookieMap is keyed by the *cookie domain*, which can be a parent
        // domain of the URI host (e.g. a Domain=example.com cookie lives under
        // "example.com" but is also sent to sub.example.com). Delete every key
        // whose domain the host matches, not just the literal host key.
        val host = uri.host ?: return
        lock.withLock {
            val matchingDomains =
                cookieMap.keys.filter { domain ->
                    host.equals(domain, ignoreCase = true) ||
                        host.endsWith(".$domain", ignoreCase = true)
                }
            if (matchingDomains.isEmpty()) return@withLock
            prefs
                .edit()
                .apply {
                    matchingDomains.forEach { remove(it) }
                }.apply()
            matchingDomains.forEach { cookieMap.remove(it) }
        }
    }

    override fun get(uri: URI): List<HttpCookie> {
        // CookieHandler callers may hand us non-http(s) URIs (e.g. jar:/file:);
        // toHttpUrlOrNull() is null there — the old `!!` turned that into an NPE
        // escaping the CookieStore contract. Empty list = "no cookies for this URI".
        val url =
            runCatching { uri.toURL().toHttpUrlOrNull() }.getOrNull()
                ?: return emptyList()
        return get(url).map {
            it.toHttpCookie()
        }
    }

    fun get(url: HttpUrl): List<Cookie> =
        lock.withLock {
            cookieMap.values.flatten().filter { !it.hasExpired() && it.matches(url) }
        }

    override fun add(
        uri: URI?,
        cookie: HttpCookie,
    ) {
        lock.withLock {
            val cookie = cookie.toCookie(uri?.host) ?: return@withLock
            val cookiesForDomain = cookieMap[cookie.domain].orEmpty().toMutableList()
            // RFC 6265 cookie identity is (name, domain, path): replace only a
            // cookie with the same name AND path — same-name cookies scoped to
            // different paths must coexist (mirrors remove()'s name+path match).
            val pos = cookiesForDomain.indexOfFirst { it.name == cookie.name && it.path == cookie.path }
            if (pos == -1) {
                cookiesForDomain.add(cookie)
            } else {
                cookiesForDomain[pos] = cookie
            }
            cookieMap[cookie.domain] = cookiesForDomain
            saveToDisk(setOf(cookie.domain))
        }
    }

    override fun getCookies(): List<HttpCookie> =
        lock.withLock {
            cookieMap.values.flatMap {
                it.filter { !it.hasExpired() }.map {
                    it.toHttpCookie()
                }
            }
        }

    fun getStoredCookies(): List<Cookie> =
        lock.withLock {
            // CEF/WebView sync pushes these to setCookie; expired cookies must
            // not be resurrected into a fresh cookie jar.
            cookieMap.values.flatMap { it }.filter { !it.hasExpired() }
        }

    override fun getURIs(): List<URI> =
        lock.withLock {
            cookieMap.keys.toList().map {
                URI("http://$it")
            }
        }

    override fun remove(
        uri: URI?,
        cookie: HttpCookie,
    ): Boolean =
        lock.withLock {
            val cookie = cookie.toCookie(uri?.host) ?: return@withLock false
            val cookies = cookieMap[cookie.domain].orEmpty()
            val index =
                cookies.indexOfFirst {
                    it.name == cookie.name &&
                        it.path == cookie.path
                }
            if (index >= 0) {
                val newList = cookies.toMutableList()
                newList.removeAt(index)
                cookieMap[cookie.domain] = newList.toList()
                saveToDisk(setOf(cookie.domain))
                true
            } else {
                false
            }
        }

    private fun saveToDisk(domains: Set<String>) {
        // Get cookies to be stored in disk
        prefs
            .edit()
            .apply {
                domains.forEach { domain ->
                    val newValues =
                        cookieMap[domain]
                            .orEmpty()
                            .asSequence()
                            .filter { it.persistent && !it.hasExpired() }
                            .map(Cookie::toString)
                            .toSet()
                    if (newValues.isNotEmpty()) {
                        remove(domain)
                        putStringSet(domain, newValues)
                    } else {
                        remove(domain)
                    }
                }
            }.apply()
    }

    private fun Cookie.hasExpired() = System.currentTimeMillis() >= expiresAt

    /**
     * True when [domain] is itself a public suffix ("github.io", "co.uk"), which
     * RFC 6265 §5.3 forbids as a Domain attribute value. Falls back to rejecting
     * bare single-label suffixes (".com") when the embedded public-suffix list
     * cannot be read.
     */
    private fun isPublicSuffix(domain: String): Boolean =
        try {
            publicSuffixDatabase.getEffectiveTldPlusOne(domain) == null
        } catch (_: Exception) {
            domain.indexOf('.') < 0
        }

    private fun HttpCookie.toCookie(urlDomain: String?): Cookie? {
        val builder =
            Cookie
                .Builder()
                .name(name)
                .value(value)
                .path(path ?: "/")
        // RFC 6265 §5.2.3: the presence of any Domain attribute yields a domain cookie; a
        // leading dot is ignored, so `Domain=example.com` and `Domain=.example.com` are
        // equivalent. Only an absent Domain produces a host-only cookie (scoped to the
        // request host). Builder.domain() marks the cookie as a domain cookie but rejects a
        // leading dot instead of stripping it, so normalize it away first.
        val cookieDomain = domain?.trimStart('.')?.takeIf { it.isNotEmpty() }
        if (cookieDomain != null) {
            // RFC 6265 §5.3: a Domain attribute that is a public suffix must be
            // rejected — Domain=.github.io would otherwise be stored and leak
            // to every *.github.io site.
            if (isPublicSuffix(cookieDomain)) {
                return null
            }
            builder.domain(cookieDomain)
        } else {
            builder.hostOnlyDomain(urlDomain ?: return null)
        }
        return builder
            .apply {
                if (maxAge != -1L) {
                    expiresAt(System.currentTimeMillis() + maxAge.seconds.inWholeMilliseconds)
                } else {
                    expiresAt(Long.MAX_VALUE)
                }
                if (secure) {
                    secure()
                }
                if (isHttpOnly) {
                    httpOnly()
                }
            }.build()
    }

    private fun Cookie.toHttpCookie(): HttpCookie {
        val it = this
        return HttpCookie(it.name, it.value).apply {
            domain =
                if (hostOnly) {
                    it.domain
                } else {
                    "." + it.domain
                }
            path = it.path
            secure = it.secure
            maxAge =
                if (it.persistent) {
                    (it.expiresAt.milliseconds - System.currentTimeMillis().milliseconds).inWholeSeconds
                } else {
                    -1
                }

            isHttpOnly = it.httpOnly
        }
    }
}
