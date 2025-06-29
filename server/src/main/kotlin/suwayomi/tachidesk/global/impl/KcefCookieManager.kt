package suwayomi.tachidesk.global.impl

import eu.kanade.tachiyomi.network.PersistentCookieStore
import org.cef.callback.CefCompletionCallback
import org.cef.callback.CefCookieVisitor
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.net.HttpCookie
import java.net.URI
import java.util.Date
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

class KcefCookieManager(private val cookieStore: PersistentCookieStore) : CefCookieManager() {
    @Volatile
    private var myIsDisposed = false

    override fun dispose() {
        myIsDisposed = true
    }

    fun HttpCookie.toCefCookie(): CefCookie {
        return CefCookie(
            name,
            value,
            domain,
            path,
            secure,
            isHttpOnly,
            Date(),
            null,
            maxAge >= 0,
            Date(System.currentTimeMillis() + maxAge),
        )
    }

    fun List<CefCookie>.visit(visitor: CefCookieVisitor?) {
        for ((i, cookie) in withIndex()) {
            if (visitor?.visit(cookie, i, size, BoolRef(false)) != true) {
                break
            }
        }
    }

    override fun visitAllCookies(visitor: CefCookieVisitor?): Boolean {
        if (myIsDisposed)
            return false

        cookieStore.cookies
            .map { it.toCefCookie() }
            .visit(visitor)

        return true
    }

    override fun visitUrlCookies(
        url: String?,
        includeHttpOnly: Boolean,
        visitor: CefCookieVisitor?,
    ): Boolean {
        if (myIsDisposed || url.isNullOrEmpty())
            return false

        cookieStore.get(URI(url))
            .let { if (includeHttpOnly) it.filter { it.isHttpOnly } else it }
            .map { it.toCefCookie() }
            .visit(visitor)

        return true
    }

    override fun setCookie(
        url: String?,
        cookie: CefCookie?,
    ): Boolean {
        if (myIsDisposed)
            return false
        cookie ?: return true

        cookieStore.add(
            URI("https://" + cookie.domain.removePrefix(".")),
            HttpCookie(cookie.name, cookie.value).apply {
                path = cookie.path
                domain = cookie.domain
                maxAge =
                    if (!cookie.hasExpires) {
                        -1
                    } else {
                        (cookie.expires.time.milliseconds - System.currentTimeMillis().milliseconds).inWholeSeconds
                    }
                isHttpOnly = cookie.httponly
                secure = cookie.secure
            },
        )
        return true
    }

    override fun deleteCookies(url: String?, cookieName: String?): Boolean {
        if (myIsDisposed)
            return false
        cookieStore.remove(URI("https://" + url!!.removePrefix(".")))
        return true
    }

    override fun flushStore(handler: CefCompletionCallback?): Boolean {
        if (myIsDisposed)
            return false
        return true
    }
}
