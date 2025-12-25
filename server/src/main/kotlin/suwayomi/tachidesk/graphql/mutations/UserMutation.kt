package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import io.javalin.http.Context
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.AuthCookieUtil.createAuthCookie
import suwayomi.tachidesk.server.user.UserType

class UserMutation {
    data class LoginInput(
        val clientMutationId: String? = null,
        val username: String,
        val password: String,
    )

    data class LoginPayload(
        val clientMutationId: String?,
        val accessToken: String,
        val refreshToken: String,
    )

    fun login(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: LoginInput,
    ): LoginPayload {
        if (dataFetchingEnvironment.getAttribute(Attribute.TachideskUser) !is UserType.Visitor) {
            throw IllegalArgumentException("Cannot login while already logged-in")
        }
        val isValid =
            input.username == serverConfig.authUsername.value &&
                input.password == serverConfig.authPassword.value
        if (isValid) {
            val jwt = Jwt.generateJwt()

            // Set tokens as persistent cookies so they survive browser restarts
            // Cookie Max-Age matches refresh token expiry to prevent browser from clearing it
            // JWT internal expiry is still short - server validates and auto-refreshes as needed
            val ctx = dataFetchingEnvironment.graphQlContext.get<Context>(Context::class)
            val cookieMaxAge = serverConfig.jwtRefreshExpiry.value.inWholeSeconds.toInt()
            ctx.cookie(createAuthCookie(ctx, "suwayomi-server-token", jwt.accessToken, cookieMaxAge))
            ctx.cookie(createAuthCookie(ctx, "suwayomi-server-refresh-token", jwt.refreshToken, cookieMaxAge))

            return LoginPayload(
                clientMutationId = input.clientMutationId,
                accessToken = jwt.accessToken,
                refreshToken = jwt.refreshToken,
            )
        } else {
            throw Exception("Incorrect username or password.")
        }
    }

    data class LogoutInput(
        val clientMutationId: String? = null,
    )

    data class LogoutPayload(
        val clientMutationId: String?,
        val success: Boolean,
    )

    fun logout(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: LogoutInput,
    ): LogoutPayload {
        if (dataFetchingEnvironment.getAttribute(Attribute.TachideskUser) is UserType.Visitor) {
            throw IllegalArgumentException("Must be logged in to logout")
        }

        // Clear the authentication cookies by setting them with maxAge=0
        val ctx = dataFetchingEnvironment.graphQlContext.get<Context>(Context::class)
        ctx.cookie(createAuthCookie(ctx, "suwayomi-server-token", "", 0))
        ctx.cookie(createAuthCookie(ctx, "suwayomi-server-refresh-token", "", 0))

        return LogoutPayload(
            clientMutationId = input.clientMutationId,
            success = true,
        )
    }

    data class RefreshTokenInput(
        val clientMutationId: String? = null,
        val refreshToken: String,
    )

    data class RefreshTokenPayload(
        val clientMutationId: String?,
        val accessToken: String,
    )

    fun refreshToken(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: RefreshTokenInput,
    ): RefreshTokenPayload {
        val accessToken = Jwt.refreshJwt(input.refreshToken)

        // Set the new access token as a persistent cookie with long Max-Age
        val ctx = dataFetchingEnvironment.graphQlContext.get<Context>(Context::class)
        val cookieMaxAge = serverConfig.jwtRefreshExpiry.value.inWholeSeconds.toInt()
        ctx.cookie(createAuthCookie(ctx, "suwayomi-server-token", accessToken, cookieMaxAge))

        return RefreshTokenPayload(
            clientMutationId = input.clientMutationId,
            accessToken = accessToken,
        )
    }
}
