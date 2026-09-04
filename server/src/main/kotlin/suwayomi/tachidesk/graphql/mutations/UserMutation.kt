@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
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
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.SessionVersion
import suwayomi.tachidesk.server.user.UserCodeService
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import suwayomi.tachidesk.server.user.UserType
import java.util.concurrent.CompletableFuture
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
    ): CompletableFuture<LoginPayload> {
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
            return future {
                val jwt = Jwt.generateJwt(user[UserAccountTable.id].value)
                LoginPayload(
                    clientMutationId = input.clientMutationId,
                    accessToken = jwt.accessToken,
                    refreshToken = jwt.refreshToken,
                )
            }
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

    fun refreshToken(input: RefreshTokenInput): CompletableFuture<RefreshTokenPayload> =
        future {
            val accessToken = Jwt.refreshJwt(input.refreshToken)

            RefreshTokenPayload(
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

        val userExists =
            transaction {
                UserAccountTable
                    .selectAll()
                    .where { UserAccountTable.username.lowerCase() eq username.lowercase() }
                    .isNotEmpty()
            }
        if (userExists) {
            throw Exception("Username already exists")
        } else {
            UserCodeService.createUser(username, password)
        }

        return RegisterPayload(
            clientMutationId = clientMutationId,
        )
    }

    data class CreateRecoveryCodeInput(
        val clientMutationId: String? = null,
        val userId: Int,
    )

    data class CreateRecoveryCodePayload(
        val clientMutationId: String?,
        val code: String,
        val expiresAt: Long,
    )

    @GraphQLDescription("Issues a one-time recovery code bound to CreateRecoveryCodeInput.userId.")
    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_USERS)
    fun createRecoveryCode(
        @GraphQLIgnore
        userId: Int,
        input: CreateRecoveryCodeInput,
    ): CreateRecoveryCodePayload {
        val issued = UserCodeService.createRecoveryCode(input.userId, userId)

        return CreateRecoveryCodePayload(
            clientMutationId = input.clientMutationId,
            code = issued.code,
            expiresAt = issued.expiresAt,
        )
    }

    data class CreateRegistrationCodeInput(
        val clientMutationId: String? = null,
    )

    data class CreateRegistrationCodePayload(
        val clientMutationId: String?,
        val code: String,
        val expiresAt: Long,
    )

    @GraphQLDescription("Issues a one-time registration code.")
    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_USERS)
    fun createRegistrationCode(
        @GraphQLIgnore
        userId: Int,
        input: CreateRegistrationCodeInput,
    ): CreateRegistrationCodePayload {
        val issued = UserCodeService.createRegistrationCode(userId)

        return CreateRegistrationCodePayload(
            clientMutationId = input.clientMutationId,
            code = issued.code,
            expiresAt = issued.expiresAt,
        )
    }

    data class RevokeUserCodeInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class RevokeUserCodePayload(
        val clientMutationId: String?,
    )

    @GraphQLDescription("Revokes an outstanding user code.")
    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_USERS)
    fun revokeUserCode(input: RevokeUserCodeInput): RevokeUserCodePayload {
        UserCodeService.revokeCode(input.id)

        return RevokeUserCodePayload(
            clientMutationId = input.clientMutationId,
        )
    }

    data class RedeemRecoveryCodeInput(
        val clientMutationId: String? = null,
        val code: String,
        val newPassword: String,
    )

    data class RedeemRecoveryCodePayload(
        val clientMutationId: String?,
        val user: GqlUserType,
        val accessToken: String,
        val refreshToken: String,
    )

    @GraphQLDescription("Redeems a recovery code with a self-chosen new password.")
    fun redeemRecoveryCode(input: RedeemRecoveryCodeInput): CompletableFuture<RedeemRecoveryCodePayload> {
        val redeemedUserId = UserCodeService.redeemRecoveryCode(input.code, input.newPassword)

        return future {
            val jwt = Jwt.generateJwt(redeemedUserId)
            val user =
                transaction {
                    UserAccountTable
                        .selectAll()
                        .where { UserAccountTable.id eq redeemedUserId }
                        .first()
                        .let { GqlUserType(it) }
                }

            RedeemRecoveryCodePayload(
                clientMutationId = input.clientMutationId,
                user = user,
                accessToken = jwt.accessToken,
                refreshToken = jwt.refreshToken,
            )
        }
    }

    data class RedeemRegistrationCodeInput(
        val clientMutationId: String? = null,
        val code: String,
        val username: String,
        val password: String,
    )

    data class RedeemRegistrationCodePayload(
        val clientMutationId: String?,
        val user: GqlUserType,
        val accessToken: String,
        val refreshToken: String,
    )

    @GraphQLDescription("Redeems a registration code with a username and a self-chosen password.")
    fun redeemRegistrationCode(input: RedeemRegistrationCodeInput): CompletableFuture<RedeemRegistrationCodePayload> {
        val redeemedUserId =
            UserCodeService.redeemRegistrationCode(input.code, input.username, input.password)

        return future {
            val jwt = Jwt.generateJwt(redeemedUserId)
            val user =
                transaction {
                    UserAccountTable
                        .selectAll()
                        .where { UserAccountTable.id eq redeemedUserId }
                        .first()
                        .let { GqlUserType(it) }
                }

            RedeemRegistrationCodePayload(
                clientMutationId = input.clientMutationId,
                user = user,
                accessToken = jwt.accessToken,
                refreshToken = jwt.refreshToken,
            )
        }
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
    fun updateUser(
        @GraphQLIgnore
        roles: List<UserRole>,
        input: UpdateUserInput,
    ): UpdateUserPayload {
        val (clientMutationId, userId, permissions, role) = input
        require(userId != 1) {
            "The built-in admin user cannot be modified"
        }
        role?.let { role ->
            require(role != UserRole.VISITOR) {
                "The VISITOR role cannot be granted"
            }
            require(UserRole.ADMIN in roles) {
                "Only ADMIN users can grant roles"
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
        val newPassword: String,
        val oldPassword: String,
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
        require(userId != 1) {
            "The built-in admin user's password is managed by the server auth settings"
        }

        val (clientMutationId, newPassword, oldPassword) = input
        transaction {
            val currentPassword =
                UserAccountTable
                    .select(UserAccountTable.password)
                    .where { UserAccountTable.id eq userId }
                    .first()[UserAccountTable.password]

            require(Bcrypt.verify(currentPassword, oldPassword)) {
                "The old password is incorrect"
            }

            UserAccountTable.update({ UserAccountTable.id eq userId }) {
                it[UserAccountTable.password] = Bcrypt.encryptPassword(newPassword)
            }

            // log the user out everywhere, revoking existing access and refresh tokens
            SessionVersion.bump(userId)
        }

        return SetPasswordPayload(
            clientMutationId = clientMutationId,
        )
    }
}
