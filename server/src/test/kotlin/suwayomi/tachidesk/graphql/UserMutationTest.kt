package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
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
                mapOf("input" to mapOf("password" to "updatedpass")),
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

    @AfterEach
    internal fun tearDown() {
        transaction {
            // restore the admin row to its seeded credentials
            UserAccountTable.update({ UserAccountTable.id eq 1 }) {
                it[UserAccountTable.username] = "admin"
                it[UserAccountTable.password] = Bcrypt.encryptPassword("password")
            }
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }
}
