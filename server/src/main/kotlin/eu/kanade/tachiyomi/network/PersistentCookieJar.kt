package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

// from TachiWeb-Server
class PersistentCookieJar(private val store: PersistentCookieStore) : CookieJar {
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        store.addAll(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return store.get(url)
    }
}
