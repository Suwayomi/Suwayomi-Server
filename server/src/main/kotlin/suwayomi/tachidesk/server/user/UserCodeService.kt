package suwayomi.tachidesk.server.user

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserCodePermissionsTable
import suwayomi.tachidesk.global.model.table.UserCodeTable
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.util.lang.isNotEmpty
import suwayomi.tachidesk.manga.model.table.CategoryTable
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

enum class UserCodePurpose {
    RECOVERY,
    REGISTRATION,
}

class UserCodeRedemptionException : Exception("Invalid or expired code")

object UserCodeService {
    private val secureRandom = SecureRandom()

    private val RECOVERY_TTL = 1.days
    private val REGISTRATION_TTL = 7.days

    data class IssuedCode(
        val code: String,
        val expiresAt: Long,
    )

    data class OutstandingCode(
        val id: Int,
        val purpose: UserCodePurpose,
        val user: Int?,
        val createdBy: Int,
        val createdAt: Long,
        val expiresAt: Long,
        val permissions: List<UserPermission>?,
    )

    private fun now(): Instant = Clock.System.now()

    /**
     * Generates a 128-bit random code encoded as HEX.
     */
    fun generateCode(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.toHexString(HexFormat.UpperCase)
    }

    /**
     * Creates a recovery code bound to [userId]. Any outstanding recovery code for the user is
     * consumed first (only one active recovery code per user).
     *
     * Returns the plaintext code, which is never stored or returned again.
     */
    fun createRecoveryCode(
        userId: Int,
        issuedBy: Int,
    ): IssuedCode =
        transaction {
            require(userId != 1) {
                "The built-in admin user cannot use recovery codes"
            }

            val userExists =
                transaction {
                    UserAccountTable
                        .selectAll()
                        .where { UserAccountTable.id eq userId }
                        .isNotEmpty()
                }

            require(userExists) {
                "User not found"
            }

            val now = now()

            UserCodeTable
                .update({
                    (UserCodeTable.user eq userId) and
                        (UserCodeTable.type eq UserCodePurpose.RECOVERY.name) and
                        UserCodeTable.consumedAt.isNull()
                }) {
                    it[UserCodeTable.consumedAt] = now.epochSeconds
                }

            val code = generateCode()
            val expiresAt = now + RECOVERY_TTL

            UserCodeTable.insert {
                it[UserCodeTable.user] = userId
                it[UserCodeTable.type] = UserCodePurpose.RECOVERY.name
                it[UserCodeTable.codeHash] = Bcrypt.encryptPassword(code)
                it[UserCodeTable.createdBy] = issuedBy
                it[UserCodeTable.createdAt] = now.epochSeconds
                it[UserCodeTable.expiresAt] = expiresAt.epochSeconds
            }

            IssuedCode(code = code, expiresAt = expiresAt.epochSeconds)
        }

    /**
     * Creates a registration code. When [userPermissions] is non-null, that permission set is
     * bound to the code and granted to the account created on redemption (an empty list grants
     * no permissions); otherwise the account gets the default permissions.
     *
     * Returns the plaintext code, which is never stored or returned again.
     */
    fun createRegistrationCode(
        issuedBy: Int,
        userPermissions: List<UserPermission>?,
    ): IssuedCode =
        transaction {
            val now = now()
            val code = generateCode()
            val expiresAt = now + REGISTRATION_TTL

            val id =
                UserCodeTable.insertAndGetId {
                    it[UserCodeTable.type] = UserCodePurpose.REGISTRATION.name
                    it[UserCodeTable.codeHash] = Bcrypt.encryptPassword(code)
                    it[UserCodeTable.createdBy] = issuedBy
                    it[UserCodeTable.createdAt] = now.epochSeconds
                    it[UserCodeTable.expiresAt] = expiresAt.epochSeconds
                    it[UserCodeTable.hasPermissions] = userPermissions != null
                }

            if (!userPermissions.isNullOrEmpty()) {
                UserCodePermissionsTable.batchInsert(userPermissions) {
                    this[UserCodePermissionsTable.userCode] = id
                    this[UserCodePermissionsTable.permission] = it.name
                }
            }

            IssuedCode(code = code, expiresAt = expiresAt.epochSeconds)
        }

    /**
     * Redeems a recovery code with a self-chosen new password.
     *
     * Returns the id of the recovered user. The account's session version is bumped, logging
     * the user out everywhere.
     */
    fun redeemRecoveryCode(
        code: String,
        newPassword: String,
    ): Int {
        val now = now()

        val match =
            findUnconsumedCode(UserCodePurpose.RECOVERY, code, now)
                ?: throw UserCodeRedemptionException()

        val codeId = match[UserCodeTable.id].value
        val userId =
            requireNotNull(match[UserCodeTable.user]?.value) {
                "recovery code is not bound to a user"
            }

        if (!claimCode(codeId, now)) {
            throw UserCodeRedemptionException()
        }

        transaction {
            UserAccountTable
                .update({ UserAccountTable.id eq userId }) {
                    it[UserAccountTable.password] = Bcrypt.encryptPassword(newPassword)
                }
        }

        SessionVersion.bump(userId)

        return userId
    }

    /**
     * Redeems a registration code with a username and a self-chosen password, creating the
     * account with the permission set bound to the code (or the default permissions when the
     * code carries none) and the USER role.
     *
     * Returns the id of the newly created user.
     */
    fun redeemRegistrationCode(
        code: String,
        username: String,
        password: String,
    ): Int {
        val now = now()

        val match =
            findUnconsumedCode(UserCodePurpose.REGISTRATION, code, now)
                ?: throw UserCodeRedemptionException()

        val userExists =
            transaction {
                UserAccountTable
                    .selectAll()
                    .where { UserAccountTable.username.lowerCase() eq username.lowercase() }
                    .isNotEmpty()
            }

        if (userExists) {
            throw Exception("Username already exists")
        }

        val codeId = match[UserCodeTable.id].value

        if (!claimCode(codeId, now)) {
            throw UserCodeRedemptionException()
        }

        val permissions =
            if (match[UserCodeTable.hasPermissions]) {
                transaction {
                    UserCodePermissionsTable
                        .selectAll()
                        .where { UserCodePermissionsTable.userCode eq codeId }
                        .mapNotNull { permission ->
                            UserPermission.entries.find { it.name == permission[UserCodePermissionsTable.permission] }
                        }
                }
            } else {
                null
            }

        val userId = createUser(username, password, permissions)

        // backfill the code row with the new user for the audit trail
        transaction {
            UserCodeTable
                .update({ UserCodeTable.id eq codeId }) {
                    it[UserCodeTable.user] = userId
                }
        }

        return userId
    }

    fun listOutstandingCodes(userId: Int? = null): List<OutstandingCode> {
        val now = now()

        val base =
            UserCodeTable.consumedAt.isNull() and (UserCodeTable.expiresAt greater now.epochSeconds)

        val where =
            if (userId != null) {
                base and (UserCodeTable.user eq userId)
            } else {
                base
            }

        return transaction {
            val rows =
                UserCodeTable
                    .selectAll()
                    .where { where }
                    .toList()

            val codeIds = rows.map { it[UserCodeTable.id].value }

            // Load the bound permission set for the codes that carry one, in a single query.
            val permissionSets: Map<Int, List<UserPermission>> =
                if (codeIds.isEmpty()) {
                    emptyMap()
                } else {
                    UserCodePermissionsTable
                        .selectAll()
                        .where { UserCodePermissionsTable.userCode inList codeIds }
                        .groupBy(
                            { it[UserCodePermissionsTable.userCode].value },
                            { row ->
                                UserPermission.entries.find { it.name == row[UserCodePermissionsTable.permission] }
                            },
                        ).mapValues {
                            it.value.filterNotNull()
                        }
                }

            rows.map { row ->
                val codeId = row[UserCodeTable.id].value
                OutstandingCode(
                    id = codeId,
                    purpose = UserCodePurpose.valueOf(row[UserCodeTable.type]),
                    user = row[UserCodeTable.user]?.value,
                    createdBy = row[UserCodeTable.createdBy],
                    createdAt = row[UserCodeTable.createdAt],
                    expiresAt = row[UserCodeTable.expiresAt],
                    // null = the code was created without a permission set (defaults apply);
                    // an empty list = the code was created with no permissions.
                    permissions =
                        if (row[UserCodeTable.hasPermissions]) {
                            permissionSets[codeId].orEmpty()
                        } else {
                            null
                        },
                )
            }
        }
    }

    fun revokeCode(id: Int) {
        val now = now()
        if (!claimCode(id, now)) {
            throw UserCodeRedemptionException()
        }
    }

    /**
     * Creates a user account with the USER role. When [permissions] is null, the default
     * permissions are granted; otherwise exactly the given set is granted (an empty list grants
     * no permissions).
     */
    fun createUser(
        username: String,
        password: String,
        permissions: List<UserPermission>?,
    ): Int {
        require(username.isNotBlank()) {
            "Username cannot be blank"
        }
        require(username.length <= 64) {
            "Username too long"
        }

        return transaction {
            val userId =
                UserAccountTable
                    .insertAndGetId {
                        it[UserAccountTable.username] = username
                        it[UserAccountTable.password] = Bcrypt.encryptPassword(password)
                    }.value

            val permissions = permissions ?: UserPermission.defaultPermissions
            if (permissions.isNotEmpty()) {
                UserPermissionsTable.batchInsert(permissions) {
                    this[UserPermissionsTable.user] = userId
                    this[UserPermissionsTable.permission] = it.name
                }
            }

            UserRolesTable.insert {
                it[UserRolesTable.user] = userId
                it[UserRolesTable.role] = UserRole.USER.name
            }

            CategoryTable.insert {
                it[CategoryTable.name] = Category.DEFAULT_CATEGORY_NAME
                it[CategoryTable.isDefault] = true
                it[CategoryTable.isDefaultCategory] = true
                it[CategoryTable.user] = userId
            }

            userId
        }
    }

    /**
     * Finds an unconsumed, unexpired code of [purpose] by bcrypt-verifying its hash against
     * [code]. The candidate set is tiny (one recovery code per user + a handful of
     * registration codes), so a scan with slow-by-design bcrypt verification is fine.
     */
    private fun findUnconsumedCode(
        purpose: UserCodePurpose,
        code: String,
        now: Instant,
    ): ResultRow? =
        transaction {
            UserCodeTable
                .selectAll()
                .where {
                    (UserCodeTable.type eq purpose.name) and
                        UserCodeTable.consumedAt.isNull() and
                        (UserCodeTable.expiresAt greater now.epochSeconds)
                }.firstOrNull { Bcrypt.verify(it[UserCodeTable.codeHash], code) }
        }

    /**
     * Atomically claims a code (marks it consumed). Only succeeds if the code is still
     * unconsumed and unexpired, which makes concurrent double-redemption safe.
     */
    private fun claimCode(
        id: Int,
        now: Instant,
    ): Boolean =
        transaction {
            UserCodeTable
                .update({
                    (UserCodeTable.id eq id) and
                        UserCodeTable.consumedAt.isNull() and
                        (UserCodeTable.expiresAt greater now.epochSeconds)
                }) {
                    it[UserCodeTable.consumedAt] = now.epochSeconds
                } == 1
        }
}
