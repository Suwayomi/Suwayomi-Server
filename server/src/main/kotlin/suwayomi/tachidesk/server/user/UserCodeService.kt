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

    private const val CODE_LENGTH = 26
    private const val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

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
    )

    private fun now(): Instant = Clock.System.now()

    /**
     * Generates a 128-bit random code encoded as 26 Crockford base32 characters.
     */
    fun generateCode(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)

        val sb = StringBuilder(CODE_LENGTH)
        var bitBuffer = 0
        var bitsRemaining = 0

        for (b in bytes) {
            for (bitIndex in 7 downTo 0) {
                bitBuffer = (bitBuffer shl 1) or ((b.toInt() shr bitIndex) and 1)
                bitsRemaining++
                if (bitsRemaining == 5) {
                    sb.append(CROCKFORD_ALPHABET[bitBuffer])
                    bitBuffer = 0
                    bitsRemaining = 0
                }
            }
        }

        // 128 bits = 25 full 5-bit groups + 3 leftover bits for the final character
        if (sb.length < CODE_LENGTH) {
            sb.append(CROCKFORD_ALPHABET[bitBuffer])
        }

        return sb.toString()
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
     * Creates an unbound registration code.
     *
     * Returns the plaintext code, which is never stored or returned again.
     */
    fun createRegistrationCode(issuedBy: Int): IssuedCode =
        transaction {
            val now = now()
            val code = generateCode()
            val expiresAt = now + REGISTRATION_TTL

            UserCodeTable.insert {
                it[UserCodeTable.type] = UserCodePurpose.REGISTRATION.name
                it[UserCodeTable.codeHash] = Bcrypt.encryptPassword(code)
                it[UserCodeTable.createdBy] = issuedBy
                it[UserCodeTable.createdAt] = now.epochSeconds
                it[UserCodeTable.expiresAt] = expiresAt.epochSeconds
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
     * account with the default permissions and the USER role.
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

        val codeId = match[UserCodeTable.id].value

        if (!claimCode(codeId, now)) {
            throw UserCodeRedemptionException()
        }

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

        val userId = createUser(username, password)

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
            UserCodeTable
                .selectAll()
                .where { where }
                .map {
                    OutstandingCode(
                        id = it[UserCodeTable.id].value,
                        purpose = UserCodePurpose.valueOf(it[UserCodeTable.type]),
                        user = it[UserCodeTable.user]?.value,
                        createdBy = it[UserCodeTable.createdBy],
                        createdAt = it[UserCodeTable.createdAt],
                        expiresAt = it[UserCodeTable.expiresAt],
                    )
                }
        }
    }

    fun revokeCode(id: Int) {
        val now = now()
        if (!claimCode(id, now)) {
            throw Exception("Invalid or expired code")
        }
    }

    /**
     * Creates a user account with the default permissions and the USER role.
     */
    fun createUser(
        username: String,
        password: String,
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

            UserPermissionsTable.batchInsert(UserPermission.defaultPermissions) {
                this[UserPermissionsTable.user] = userId
                this[UserPermissionsTable.permission] = it.name
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
