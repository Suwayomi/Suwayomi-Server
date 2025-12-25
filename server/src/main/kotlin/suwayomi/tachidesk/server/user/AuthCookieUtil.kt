package suwayomi.tachidesk.server.user

import io.javalin.http.Context
import io.javalin.http.Cookie
import io.javalin.http.SameSite

object AuthCookieUtil {
    /** Create an auth cookie with secure flag set based on request scheme */
    fun createAuthCookie(
        ctx: Context,
        name: String,
        value: String,
        maxAge: Int,
    ): Cookie =
        Cookie(
            name = name,
            value = value,
            maxAge = maxAge,
            sameSite = SameSite.STRICT,
            isHttpOnly = true,
            secure = ctx.scheme() == "https",
            path = "/",
        )
}
