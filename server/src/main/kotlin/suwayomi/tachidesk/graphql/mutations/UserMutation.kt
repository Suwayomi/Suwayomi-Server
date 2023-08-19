package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.global.model.table.UserTable
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup
import suwayomi.tachidesk.server.user.UserType

class UserMutation {

    data class LoginInput(
        val clientMutationId: String? = null,
        val username: String,
        val password: String
    )
    data class LoginPayload(
        val clientMutationId: String?,
        val accessToken: String,
        val refreshToken: String
    )
    fun login(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: LoginInput
    ): LoginPayload {
        if (dataFetchingEnvironment.getAttribute(JavalinSetup.Attribute.TachideskUser) !is UserType.Visitor) {
            throw IllegalArgumentException("Cannot login while already logged-in")
        }
        val user = transaction {
            UserTable.select { UserTable.username.lowerCase() eq input.username.lowercase() }.firstOrNull()
        }
        if (user != null && Bcrypt.verify(user[UserTable.password], input.password)) {
            val jwt = Jwt.generateJwt(user[UserTable.id].value)
            return LoginPayload(
                clientMutationId = input.clientMutationId,
                accessToken = jwt.accessToken,
                refreshToken = jwt.refreshToken
            )
        } else {
            throw Exception("Incorrect username or password.")
        }
    }

    data class RefreshTokenInput(
        val clientMutationId: String? = null,
        val refreshToken: String
    )
    data class RefreshTokenPayload(
        val clientMutationId: String?,
        val accessToken: String,
        val refreshToken: String
    )
    fun refreshToken(
        input: RefreshTokenInput
    ): RefreshTokenPayload {
        val jwt = Jwt.refreshJwt(input.refreshToken)

        return RefreshTokenPayload(
            clientMutationId = input.clientMutationId,
            accessToken = jwt.accessToken,
            refreshToken = jwt.refreshToken
        )
    }
}
