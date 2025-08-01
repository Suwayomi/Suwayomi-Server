package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.serverConfig
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
            return LoginPayload(
                clientMutationId = input.clientMutationId,
                accessToken = jwt.accessToken,
                refreshToken = jwt.refreshToken,
            )
        } else {
            throw Exception("Incorrect username or password.")
        }
    }

    data class RefreshTokenInput(
        val clientMutationId: String? = null,
        val refreshToken: String,
    )

    data class RefreshTokenPayload(
        val clientMutationId: String?,
        val accessToken: String,
    )

    fun refreshToken(input: RefreshTokenInput): RefreshTokenPayload {
        val accessToken = Jwt.refreshJwt(input.refreshToken)

        return RefreshTokenPayload(
            clientMutationId = input.clientMutationId,
            accessToken = accessToken,
        )
    }
}
