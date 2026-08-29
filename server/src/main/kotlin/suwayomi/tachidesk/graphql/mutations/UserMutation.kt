@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.manga.impl.util.lang.isNotEmpty
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.graphql.types.UserType as GqlUserType

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

        val user =
            transaction {
                UserAccountTable
                    .selectAll()
                    .where { UserAccountTable.username.lowerCase() eq input.username.lowercase() }
                    .firstOrNull()
            }
        if (user != null && Bcrypt.verify(user[UserAccountTable.password], input.password)) {
            val jwt = Jwt.generateJwt(user[UserAccountTable.id].value)
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

    data class RegisterInput(
        val clientMutationId: String? = null,
        val username: String,
        val password: String,
    )

    data class RegisterPayload(
        val clientMutationId: String?,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_USERS)
    fun register(input: RegisterInput): RegisterPayload {
        val (clientMutationId, username, password) = input
        transaction {
            val userExists =
                UserAccountTable
                    .selectAll()
                    .where { UserAccountTable.username.lowerCase() eq username.lowercase() }
                    .isNotEmpty()
            if (userExists) {
                throw Exception("Username already exists")
            } else {
                val userId =
                    UserAccountTable
                        .insertAndGetId {
                            it[UserAccountTable.username] = username
                            it[UserAccountTable.password] = Bcrypt.encryptPassword(password)
                        }.value

                // grant the default permissions and a non-admin role
                UserPermissionsTable.batchInsert(UserPermission.defaultPermissions) {
                    this[UserPermissionsTable.user] = userId
                    this[UserPermissionsTable.permission] = it.name
                }

                UserRolesTable.insert {
                    it[UserRolesTable.user] = userId
                    it[UserRolesTable.role] = UserRole.USER.name
                }
            }
        }

        return RegisterPayload(
            clientMutationId = clientMutationId,
        )
    }

    data class UpdateUserInput(
        val clientMutationId: String? = null,
        val userId: Int,
        val permissions: List<UserPermission>? = null,
        val role: UserRole? = null,
    )

    data class UpdateUserPayload(
        val clientMutationId: String?,
        val user: GqlUserType,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_USERS)
    fun updateUser(input: UpdateUserInput): UpdateUserPayload {
        val (clientMutationId, userId, permissions, role) = input
        require(userId != 1) {
            "The built-in admin user cannot be modified"
        }
        role?.let { role ->
            require(role != UserRole.VISITOR) {
                "The VISITOR role cannot be granted"
            }
        }

        transaction {
            UserAccountTable
                .selectAll()
                .where { UserAccountTable.id eq userId }
                .firstOrNull()
                ?: throw IllegalArgumentException("user $userId not found")

            if (permissions != null) {
                UserPermissionsTable.deleteWhere { UserPermissionsTable.user eq userId }

                UserPermissionsTable.batchInsert(permissions) { permission ->
                    this[UserPermissionsTable.user] = userId
                    this[UserPermissionsTable.permission] = permission.name
                }
            }

            if (role != null) {
                UserRolesTable.deleteWhere { UserRolesTable.user eq userId }

                UserRolesTable.insert {
                    it[UserRolesTable.user] = userId
                    it[UserRolesTable.role] = role.name
                }
            }
        }

        val user =
            transaction {
                UserAccountTable
                    .selectAll()
                    .where { UserAccountTable.id eq userId }
                    .first()
                    .let { GqlUserType(it) }
            }

        return UpdateUserPayload(
            clientMutationId = clientMutationId,
            user = user,
        )
    }

    data class SetPasswordInput(
        val clientMutationId: String? = null,
        val password: String,
    )

    data class SetPasswordPayload(
        val clientMutationId: String?,
    )

    @RequireAuth
    fun setPassword(
        @GraphQLIgnore
        userId: Int,
        input: SetPasswordInput,
    ): SetPasswordPayload {
        val (clientMutationId, password) = input
        transaction {
            UserAccountTable.update({ UserAccountTable.id eq userId }) {
                it[UserAccountTable.password] = Bcrypt.encryptPassword(password)
            }
        }

        return SetPasswordPayload(
            clientMutationId = clientMutationId,
        )
    }
}
