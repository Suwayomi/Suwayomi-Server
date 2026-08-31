package suwayomi.tachidesk.graphql

import com.expediagroup.graphql.server.types.GraphQLResponse
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserCodeTable
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.server.user.SessionVersion
import suwayomi.tachidesk.server.user.UserCodePurpose
import suwayomi.tachidesk.server.user.UserCodeService
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserMutationTest : GraphQLTest() {
    private val visitor: UserType = UserType.Visitor

    /**
     * Set the admin user (id 1) credentials in the database and enable multi-user mode so that
     * `login` verifies against [UserAccountTable] with Bcrypt instead of the serverConfig values.
     */
    private fun setAdminCredentials(
        username: String,
        password: String,
    ) {
        transaction {
            UserAccountTable.update({ UserAccountTable.id eq 1 }) {
                it[UserAccountTable.username] = username
                it[UserAccountTable.password] = Bcrypt.encryptPassword(password)
            }
        }
    }

    private fun login(
        username: String,
        password: String,
    ): GraphQLResponse<*> =
        graphql(
            """
            mutation(${'$'}input: LoginInput!) {
                login(input: ${'$'}input) {
                    accessToken
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("username" to username, "password" to password)),
            user = visitor,
        )

    private fun createRecoveryCodeFor(userId: Int): Pair<String, Long> {
        val response =
            graphql(
                """
                mutation(${'$'}input: CreateRecoveryCodeInput!) {
                    createRecoveryCode(input: ${'$'}input) {
                        code
                        expiresAt
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("userId" to userId)),
            )
        response.assertNoErrors()
        val code = response.dataPath("createRecoveryCode", "code") as String
        val expiresAt = (response.dataPath("createRecoveryCode", "expiresAt") as String).toLong()
        return code to expiresAt
    }

    private fun createRegistrationCode(): Pair<String, Long> {
        val response =
            graphql(
                """
                mutation(${'$'}input: CreateRegistrationCodeInput!) {
                    createRegistrationCode(input: ${'$'}input) {
                        code
                        expiresAt
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf<String, Any?>()),
            )
        response.assertNoErrors()
        val code = response.dataPath("createRegistrationCode", "code") as String
        val expiresAt = (response.dataPath("createRegistrationCode", "expiresAt") as String).toLong()
        return code to expiresAt
    }

    private fun redeemRecoveryCode(
        code: String,
        newPassword: String,
    ): GraphQLResponse<*> =
        graphql(
            """
            mutation(${'$'}input: RedeemRecoveryCodeInput!) {
                redeemRecoveryCode(input: ${'$'}input) {
                    user {
                        id
                        username
                    }
                    accessToken
                    refreshToken
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("code" to code, "newPassword" to newPassword)),
            user = visitor,
        )

    private fun redeemRegistrationCode(
        code: String,
        username: String,
        password: String,
    ): GraphQLResponse<*> =
        graphql(
            """
            mutation(${'$'}input: RedeemRegistrationCodeInput!) {
                redeemRegistrationCode(input: ${'$'}input) {
                    user {
                        id
                        username
                    }
                    accessToken
                    refreshToken
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("code" to code, "username" to username, "password" to password)),
            user = visitor,
        )

    private fun userCodes(forUserId: Int? = null): GraphQLResponse<*> {
        val query =
            if (forUserId != null) {
                """
                query(${'$'}forUserId: Int) {
                    userCodes(forUserId: ${'$'}forUserId) {
                        id
                        purpose
                        user {
                            id
                        }
                    }
                }
                """.trimIndent()
            } else {
                """
                query {
                    userCodes {
                        id
                        purpose
                        user {
                            id
                        }
                    }
                }
                """.trimIndent()
            }
        return graphql(
            query,
            forUserId?.let { mapOf("forUserId" to it) },
        )
    }

    private fun revokeUserCode(codeId: Int): GraphQLResponse<*> =
        graphql(
            """
            mutation(${'$'}input: RevokeUserCodeInput!) {
                revokeUserCode(input: ${'$'}input) {
                    clientMutationId
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("id" to codeId)),
        )

    private fun seedExpiredRecoveryCode(userId: Int): String {
        val code = UserCodeService.generateCode()
        val now = System.currentTimeMillis() / 1000
        transaction {
            UserCodeTable.insert {
                it[UserCodeTable.user] = userId
                it[UserCodeTable.type] = UserCodePurpose.RECOVERY.name
                it[UserCodeTable.codeHash] = Bcrypt.encryptPassword(code)
                it[UserCodeTable.createdBy] = 1
                it[UserCodeTable.createdAt] = now - 172_800
                it[UserCodeTable.expiresAt] = now - 86_400
            }
        }
        return code
    }

    private fun seedExpiredRegistrationCode(): String {
        val code = UserCodeService.generateCode()
        val now = System.currentTimeMillis() / 1000
        transaction {
            UserCodeTable.insert {
                it[UserCodeTable.type] = UserCodePurpose.REGISTRATION.name
                it[UserCodeTable.codeHash] = Bcrypt.encryptPassword(code)
                it[UserCodeTable.createdBy] = 1
                it[UserCodeTable.createdAt] = now - 172_800
                it[UserCodeTable.expiresAt] = now - 86_400
            }
        }
        return code
    }

    @Test
    fun loginWithBasicAuth() {
        setAdminCredentials("testuser", "testpass")

        val response =
            graphql(
                """
                mutation(${'$'}input: LoginInput!) {
                    login(input: ${'$'}input) {
                        accessToken
                        refreshToken
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("username" to "testuser", "password" to "testpass")),
                user = visitor,
            )

        response.assertNoErrors()
        assertNotNull(response.dataPath("login", "accessToken"), "accessToken should be present")
        assertNotNull(response.dataPath("login", "refreshToken"), "refreshToken should be present")
    }

    @Test
    fun loginFailsWithWrongCredentials() {
        setAdminCredentials("testuser", "testpass")

        val response =
            graphql(
                """
                mutation(${'$'}input: LoginInput!) {
                    login(input: ${'$'}input) {
                        accessToken
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("username" to "testuser", "password" to "wrongpass")),
                user = visitor,
            )

        response.assertHasError()
    }

    @Test
    fun registerCreatesUser() {
        val response =
            graphql(
                """
                mutation(${'$'}input: RegisterInput!) {
                    register(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("username" to "newuser", "password" to "newpass")),
            )

        response.assertNoErrors()

        val exists =
            transaction {
                UserAccountTable.selectAll().where { UserAccountTable.username eq "newuser" }.count() > 0
            }
        assertTrue(exists, "the registered user should exist in the database")
    }

    @Test
    fun registerFailsForDuplicateUsername() {
        // admin user already exists
        val response =
            graphql(
                """
                mutation(${'$'}input: RegisterInput!) {
                    register(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("username" to "admin", "password" to "whatever")),
            )

        response.assertHasError()
    }

    @Test
    fun setPassword() {
        val response =
            graphql(
                """
                mutation(${'$'}input: SetPasswordInput!) {
                    setPassword(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("newPassword" to "updatedpass", "oldPassword" to "password")),
            )

        response.assertNoErrors()
    }

    @Test
    fun refreshToken() {
        setAdminCredentials("testuser", "testpass")

        val loginResponse =
            graphql(
                """
                mutation(${'$'}input: LoginInput!) {
                    login(input: ${'$'}input) {
                        refreshToken
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("username" to "testuser", "password" to "testpass")),
                user = visitor,
            )
        loginResponse.assertNoErrors()
        val refreshToken = loginResponse.dataPath("login", "refreshToken") as String

        val response =
            graphql(
                """
                mutation(${'$'}input: RefreshTokenInput!) {
                    refreshToken(input: ${'$'}input) {
                        accessToken
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("refreshToken" to refreshToken)),
            )

        response.assertNoErrors()
        assertNotNull(response.dataPath("refreshToken", "accessToken"), "a new accessToken should be returned")
    }

    @Test
    fun createRecoveryCodeReturnsCodeWith24hExpiry() {
        val userId = createTestUser("codeuser")

        val response =
            graphql(
                """
                mutation(${'$'}input: CreateRecoveryCodeInput!) {
                    createRecoveryCode(input: ${'$'}input) {
                        code
                        expiresAt
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("userId" to userId)),
            )

        response.assertNoErrors()

        val code = response.dataPath("createRecoveryCode", "code") as String
        val expiresAt = (response.dataPath("createRecoveryCode", "expiresAt") as String).toLong()

        assertEquals(26, code.length, "the code should be 26 characters long")
        assertTrue(
            code.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" },
            "the code should only contain Crockford base32 characters",
        )

        val expected = (System.currentTimeMillis() / 1000) + 86_400
        assertTrue(abs(expiresAt - expected) <= 5, "expiresAt should be ~24h from now")
    }

    private fun GraphQLResponse<*>.assertForbidden() {
        assertHasError()
        assertEquals(
            true,
            errors?.any { it.message.contains("Forbidden") },
            "Expected a Forbidden error but got: $errors",
        )
    }

    @Test
    fun userCodeOperationsRequireManageUsers() {
        val regularUser = UserType.User(createTestUser("regularuser"), listOf(UserPermission.DOWNLOAD_CHAPTERS))
        val targetUser = createTestUser("targetuser")

        val createRecovery =
            graphql(
                """
                mutation(${'$'}input: CreateRecoveryCodeInput!) {
                    createRecoveryCode(input: ${'$'}input) {
                        code
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("userId" to targetUser)),
                user = regularUser,
            )
        createRecovery.assertForbidden()

        val createRegistration =
            graphql(
                """
                mutation(${'$'}input: CreateRegistrationCodeInput!) {
                    createRegistrationCode(input: ${'$'}input) {
                        code
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf<String, Any?>()),
                user = regularUser,
            )
        createRegistration.assertForbidden()

        val revoke =
            graphql(
                """
                mutation(${'$'}input: RevokeUserCodeInput!) {
                    revokeUserCode(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to 1)),
                user = regularUser,
            )
        revoke.assertForbidden()

        val list =
            graphql(
                """
                query {
                    userCodes {
                        id
                    }
                }
                """.trimIndent(),
                user = regularUser,
            )
        list.assertForbidden()
    }

    @Test
    fun recoveryCodeRoundTrip() {
        val userId = createTestUser("recoveryuser")
        val (code, _) = createRecoveryCodeFor(userId)

        val redeemResponse = redeemRecoveryCode(code, "newpass")
        redeemResponse.assertNoErrors()
        assertEquals(userId, (redeemResponse.dataPath("redeemRecoveryCode", "user", "id") as Number).toInt())
        assertNotNull(redeemResponse.dataPath("redeemRecoveryCode", "accessToken"), "accessToken should be present")
        assertNotNull(redeemResponse.dataPath("redeemRecoveryCode", "refreshToken"), "refreshToken should be present")

        // login with the new password works
        login("recoveryuser", "newpass").assertNoErrors()

        // login with the old password fails
        login("recoveryuser", "password").assertHasError()
    }

    @Test
    fun recoveryCodeIsSingleUse() {
        val userId = createTestUser("singleuseuser")
        val (code, _) = createRecoveryCodeFor(userId)

        redeemRecoveryCode(code, "newpass").assertNoErrors()

        redeemRecoveryCode(code, "otherpass").assertHasError()
    }

    @Test
    fun recoveryCodeRedemptionFailsWhenExpired() {
        val userId = createTestUser("expireduser")
        val code = seedExpiredRecoveryCode(userId)

        redeemRecoveryCode(code, "newpass").assertHasError()
    }

    @Test
    fun registrationCodeRedemptionFailsWhenExpired() {
        val code = seedExpiredRegistrationCode()

        redeemRegistrationCode(code, "reguser", "regpass").assertHasError()

        // no account was created
        val exists =
            transaction {
                UserAccountTable.selectAll().where { UserAccountTable.username eq "reguser" }.count() > 0
            }
        assertEquals(false, exists)
    }

    @Test
    fun loginFailsWhenAlreadyLoggedIn() {
        setAdminCredentials("testuser", "testpass")
        val loggedInUser = UserType.User(createTestUser("loggeduser"), listOf(UserPermission.DOWNLOAD_CHAPTERS))

        val response =
            graphql(
                """
                mutation(${'$'}input: LoginInput!) {
                    login(input: ${'$'}input) {
                        accessToken
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("username" to "testuser", "password" to "testpass")),
                user = loggedInUser,
            )

        response.assertHasError()
        assertEquals(
            true,
            response.errors?.any { it.message.contains("already logged-in") },
            "Expected the already-logged-in guard error but got: $response",
        )
    }

    @Test
    fun staleRefreshTokenFailsThroughGqlMutation() =
        runTest {
            val userId = createTestUser("stalerefreshuser")
            val tokens = Jwt.generateJwt(userId)

            // a password change bumps the session version, revoking the refresh token
            SessionVersion.bump(userId)

            val response =
                graphql(
                    """
                    mutation(${'$'}input: RefreshTokenInput!) {
                        refreshToken(input: ${'$'}input) {
                            accessToken
                        }
                    }
                    """.trimIndent(),
                    mapOf("input" to mapOf("refreshToken" to tokens.refreshToken)),
                )

            response.assertHasError()
        }

    @Test
    fun supersededRecoveryCodeFails() {
        val userId = createTestUser("supersededuser")
        val (code1, _) = createRecoveryCodeFor(userId)
        val (code2, _) = createRecoveryCodeFor(userId)

        // the first code was superseded by issuing the second one
        redeemRecoveryCode(code1, "newpass").assertHasError()

        // the second code still works
        redeemRecoveryCode(code2, "newpass").assertNoErrors()
    }

    @Test
    fun registrationCodeRoundTrip() {
        val (code, _) = createRegistrationCode()

        val redeemResponse = redeemRegistrationCode(code, "reguser", "regpass")
        redeemResponse.assertNoErrors()
        assertNotNull(redeemResponse.dataPath("redeemRegistrationCode", "accessToken"), "accessToken should be present")
        assertNotNull(redeemResponse.dataPath("redeemRegistrationCode", "refreshToken"), "refreshToken should be present")

        val newUserId = (redeemResponse.dataPath("redeemRegistrationCode", "user", "id") as Number).toInt()

        // the new account has the default permissions and the USER role
        val permissions =
            transaction {
                UserPermissionsTable
                    .selectAll()
                    .where { UserPermissionsTable.user eq newUserId }
                    .map { it[UserPermissionsTable.permission] }
                    .toSet()
            }
        assertEquals(UserPermission.defaultPermissions.map { it.name }.toSet(), permissions)

        val roles =
            transaction {
                UserRolesTable
                    .selectAll()
                    .where { UserRolesTable.user eq newUserId }
                    .map { it[UserRolesTable.role] }
            }
        assertEquals(listOf(UserRole.USER.name), roles)

        // login works
        login("reguser", "regpass").assertNoErrors()
    }

    @Test
    fun registrationCodeFailsForDuplicateUsername() {
        // the admin user already exists
        val (code, _) = createRegistrationCode()

        redeemRegistrationCode(code, "admin", "whatever").assertHasError()
    }

    @Test
    fun userCodesListsOutstandingCodes() {
        val userId = createTestUser("listuser")
        val (recoveryCode, _) = createRecoveryCodeFor(userId)
        val (registrationCode, _) = createRegistrationCode()

        // both outstanding codes are listed
        var response = userCodes()
        response.assertNoErrors()
        var entries = response.dataPath("userCodes") as List<Map<String, Any?>>
        assertEquals(2, entries.size)

        // revoking the recovery code removes it from the list
        val recoveryCodeId = entries.first { it["purpose"] == "RECOVERY" }["id"] as Int
        revokeUserCode(recoveryCodeId).assertNoErrors()

        response = userCodes()
        response.assertNoErrors()
        entries = response.dataPath("userCodes") as List<Map<String, Any?>>
        assertEquals(1, entries.size)
        assertEquals("REGISTRATION", entries.first()["purpose"])

        // an expired code is not listed
        seedExpiredRecoveryCode(userId)

        response = userCodes()
        response.assertNoErrors()
        assertEquals(1, (response.dataPath("userCodes") as List<*>).size)

        // the forUserId filter only matches codes bound to that user
        response = userCodes(forUserId = userId)
        response.assertNoErrors()
        assertEquals(0, (response.dataPath("userCodes") as List<*>).size)

        // redeeming the registration code consumes it
        redeemRegistrationCode(registrationCode, "reguser2", "regpass").assertNoErrors()

        response = userCodes()
        response.assertNoErrors()
        assertEquals(0, (response.dataPath("userCodes") as List<*>).size)
    }

    @Test
    fun revokeUserCodePreventsRedemption() {
        val userId = createTestUser("revokeuser")
        val (code, _) = createRecoveryCodeFor(userId)

        val response = userCodes()
        response.assertNoErrors()
        val codeId = (response.dataPath("userCodes") as List<Map<String, Any?>>).first()["id"] as Int

        revokeUserCode(codeId).assertNoErrors()

        // the revoked code can no longer be redeemed
        redeemRecoveryCode(code, "newpass").assertHasError()

        // revoking it again fails
        revokeUserCode(codeId).assertHasError()
    }

    @Test
    fun passwordChangeInvalidatesExistingTokens() =
        runTest {
            val userId = createTestUser("sessionuser")

            val tokens = Jwt.generateJwt(userId)
            assertTrue(Jwt.verifyJwt(tokens.accessToken) is UserType.User)

            SessionVersion.bump(userId)

            // the old access token is rejected...
            assertTrue(Jwt.verifyJwt(tokens.accessToken) is UserType.Visitor)

            // ...and the old refresh token can no longer mint access tokens
            assertFailsWith<IllegalArgumentException> {
                Jwt.refreshJwt(tokens.refreshToken)
            }

            // freshly minted tokens still verify
            val fresh = Jwt.generateJwt(userId)
            assertTrue(Jwt.verifyJwt(fresh.accessToken) is UserType.User)
        }

    @Test
    fun setPasswordInvalidatesExistingTokens() =
        runTest {
            val userId = createTestUser("setpassuser")

            val tokens = Jwt.generateJwt(userId)
            assertTrue(Jwt.verifyJwt(tokens.accessToken) is UserType.User)

            val response =
                graphql(
                    """
                    mutation(${'$'}input: SetPasswordInput!) {
                        setPassword(input: ${'$'}input) {
                            clientMutationId
                        }
                    }
                    """.trimIndent(),
                    mapOf("input" to mapOf("newPassword" to "newpass", "oldPassword" to "password")),
                    user = UserType.User(userId, listOf(UserPermission.DOWNLOAD_CHAPTERS)),
                )
            response.assertNoErrors()

            // the password change bumped the session version, logging the user out
            assertTrue(Jwt.verifyJwt(tokens.accessToken) is UserType.Visitor)
        }

    @Test
    fun sessionVersionIsCachedAndBumpInvalidates() =
        runTest {
            val userId = createTestUser("cacheuser")

            // the first read populates the cache
            val first = SessionVersion.current(userId)
            assertEquals(0, first)

            // a direct DB update is not visible while the cache entry is live
            transaction {
                UserAccountTable.update({ UserAccountTable.id eq userId }) {
                    it[UserAccountTable.sessionVersion] = 5
                }
            }
            assertEquals(first, SessionVersion.current(userId))

            // bump invalidates the cache and increments from the current DB value
            SessionVersion.bump(userId)
            assertEquals(6, SessionVersion.current(userId))
        }

    @AfterEach
    internal fun tearDown() {
        transaction {
            // restore the admin row to its seeded credentials
            UserAccountTable.update({ UserAccountTable.id eq 1 }) {
                it[UserAccountTable.username] = "admin"
                it[UserAccountTable.password] = Bcrypt.encryptPassword("password")
            }
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
            UserCodeTable.deleteAll()
        }
    }
}
