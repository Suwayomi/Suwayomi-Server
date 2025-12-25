package suwayomi.tachidesk.server.user

import com.auth0.jwt.exceptions.JWTVerificationException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.Context
import io.javalin.http.Header
import io.javalin.websocket.WsConnectContext
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.serverConfig

private val logger = KotlinLogging.logger {}

sealed class UserType {
    class Admin(
        val id: Int,
    ) : UserType()

    data object Visitor : UserType()
}

fun UserType.requireUser(): Int =
    when (this) {
        is UserType.Admin -> id
        UserType.Visitor -> throw UnauthorizedException()
    }

fun UserType.requireUserWithBasicFallback(ctx: Context): Int =
    when (this) {
        is UserType.Admin -> {
            id
        }

        UserType.Visitor if ctx.getAttribute(Attribute.TachideskBasic) -> {
            1
        }

        UserType.Visitor -> {
            ctx.header("WWW-Authenticate", "Basic")
            throw UnauthorizedException()
        }
    }

fun getUserFromToken(token: String?): UserType {
    if (serverConfig.authMode.value != AuthMode.UI_LOGIN) {
        return UserType.Admin(1)
    }

    if (token.isNullOrBlank()) {
        return UserType.Visitor
    }

    return Jwt.verifyJwt(token)
}

fun getUserFromContext(ctx: Context): UserType {
    fun cookieValid(): Boolean {
        val username = ctx.sessionAttribute<String>("logged-in") ?: return false
        return username == serverConfig.authUsername.value
    }

    return when (serverConfig.authMode.value) {
        // NOTE: Basic Auth is expected to have been validated by JavalinSetup
        AuthMode.NONE, AuthMode.BASIC_AUTH -> {
            UserType.Admin(1)
        }

        AuthMode.SIMPLE_LOGIN -> {
            if (cookieValid()) UserType.Admin(1) else UserType.Visitor
        }

        AuthMode.UI_LOGIN -> {
            val authentication = ctx.header(Header.AUTHORIZATION) ?: ctx.cookie("suwayomi-server-token")
            val token = authentication?.substringAfter("Bearer ") ?: ctx.queryParam("token")

            val user = getUserFromToken(token)
            if (user is UserType.Visitor) {
                // Access token is invalid/expired, try to refresh using refresh token cookie
                val refreshToken = ctx.cookie("suwayomi-server-refresh-token")
                if (!refreshToken.isNullOrBlank()) {
                    try {
                        val newAccessToken = Jwt.refreshJwt(refreshToken)
                        // Set the new access token as a cookie with long Max-Age
                        // (matches refresh token expiry so browser doesn't clear it)
                        ctx.cookie(
                            AuthCookieUtil.createAuthCookie(
                                ctx,
                                "suwayomi-server-token",
                                newAccessToken,
                                serverConfig.jwtRefreshExpiry.value.inWholeSeconds.toInt(),
                            ),
                        )
                        return UserType.Admin(1)
                    } catch (e: JWTVerificationException) {
                        logger.debug(e) { "Refresh token invalid, user remains as Visitor" }
                    }
                }
            }
            user
        }
    }
}

fun getUserFromWsContext(ctx: WsConnectContext): UserType {
    fun cookieValid(): Boolean {
        val username = ctx.sessionAttribute<String>("logged-in") ?: return false
        return username == serverConfig.authUsername.value
    }

    return when (serverConfig.authMode.value) {
        // NOTE: Basic Auth is expected to have been validated by JavalinSetup
        AuthMode.NONE, AuthMode.BASIC_AUTH -> {
            UserType.Admin(1)
        }

        AuthMode.SIMPLE_LOGIN -> {
            if (cookieValid()) UserType.Admin(1) else UserType.Visitor
        }

        AuthMode.UI_LOGIN -> {
            // Note: WebSocket connections cannot auto-refresh tokens because
            // WsConnectContext doesn't support setting response cookies.
            // Clients should ensure valid tokens before establishing WS connections.
            val authentication =
                ctx.header(Header.AUTHORIZATION) ?: ctx.header("Sec-WebSocket-Protocol") ?: ctx.cookie("suwayomi-server-token")
            val token = authentication?.substringAfter("Bearer ") ?: ctx.queryParam("token")

            getUserFromToken(token)
        }
    }
}

class UnauthorizedException : IllegalStateException("Unauthorized")

class ForbiddenException : IllegalStateException("Forbidden")
