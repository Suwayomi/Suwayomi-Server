package suwayomi.tachidesk.server.user

import io.javalin.http.Context
import io.javalin.http.Header
import io.javalin.websocket.WsConnectContext
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.serverConfig

sealed class UserType {
    class Admin(
        val id: Int,
    ) : UserType()

    class User(
        val id: Int,
        val permissions: List<Permissions>,
    ) : UserType()

    data object Visitor : UserType()
}

fun UserType.requireUser(): Int =
    when (this) {
        is UserType.Admin -> id
        is UserType.User -> id
        UserType.Visitor -> throw UnauthorizedException()
    }

fun UserType.requireUserWithBasicFallback(ctx: Context): Int =
    when (this) {
        is UserType.Admin -> id
        UserType.Visitor if ctx.getAttribute(Attribute.TachideskBasic) -> 1
        UserType.Visitor -> {
            ctx.header("WWW-Authenticate", "Basic")
            throw UnauthorizedException()
        }

        is UserType.User -> id
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

fun UserType.requirePermissions(vararg permissions: Permissions) {
    when (this) {
        is UserType.Admin -> Unit
        is UserType.User -> {
            val userPermissions = this.permissions
            if (!permissions.all { it in userPermissions }) {
                throw ForbiddenException()
            }
        }
        UserType.Visitor -> throw UnauthorizedException()
    }
}

fun getUserFromContext(ctx: Context): UserType {
    fun cookieValid(): Boolean {
        val username = ctx.sessionAttribute<String>("logged-in") ?: return false
        return username == serverConfig.authUsername.value
    }

    return when (serverConfig.authMode.value) {
        // NOTE: Basic Auth is expected to have been validated by JavalinSetup
        AuthMode.NONE, AuthMode.BASIC_AUTH -> UserType.Admin(1)
        AuthMode.SIMPLE_LOGIN -> if (cookieValid()) UserType.Admin(1) else UserType.Visitor
        AuthMode.UI_LOGIN -> {
            val authentication = ctx.header(Header.AUTHORIZATION) ?: ctx.cookie("suwayomi-server-token")
            val token = authentication?.substringAfter("Bearer ") ?: ctx.queryParam("token")

            getUserFromToken(token)
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
        AuthMode.NONE, AuthMode.BASIC_AUTH -> UserType.Admin(1)
        AuthMode.SIMPLE_LOGIN -> if (cookieValid()) UserType.Admin(1) else UserType.Visitor
        AuthMode.UI_LOGIN -> {
            val authentication =
                ctx.header(Header.AUTHORIZATION) ?: ctx.header("Sec-WebSocket-Protocol") ?: ctx.cookie("suwayomi-server-token")
            val token = authentication?.substringAfter("Bearer ") ?: ctx.queryParam("token")

            getUserFromToken(token)
        }
    }
}

class UnauthorizedException : IllegalStateException("Unauthorized")

class ForbiddenException : IllegalStateException("Forbidden")
