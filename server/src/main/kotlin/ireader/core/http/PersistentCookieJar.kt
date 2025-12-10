package ireader.core.http

import ireader.core.prefs.PreferenceStore
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(preferencesStore: PreferenceStore) : CookieJar {

    val store = PersistentCookieStore()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val commonCookies = cookies.map { it.toCommonCookie() }
        runBlocking {
            store.addCookies(url.toString(), commonCookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return runBlocking {
            store.getCookies(url.toString()).mapNotNull { it.toOkHttpCookie(url) }
        }
    }

    private fun Cookie.toCommonCookie(): ireader.core.http.Cookie {
        return ireader.core.http.Cookie(
            name = name,
            value = value,
            domain = domain,
            path = path,
            expiresAt = expiresAt,
            secure = secure,
            httpOnly = httpOnly,
            persistent = persistent
        )
    }

    private fun ireader.core.http.Cookie.toOkHttpCookie(url: HttpUrl): Cookie? {
        return Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path(path)
            .apply {
                if (expiresAt > 0) expiresAt(expiresAt)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }
}
