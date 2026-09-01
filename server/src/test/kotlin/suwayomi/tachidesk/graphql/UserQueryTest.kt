package suwayomi.tachidesk.graphql

import com.expediagroup.graphql.server.types.GraphQLResponse
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserQueryTest : GraphQLTest() {
    private var originalDownloadAsCbz: Boolean = false

    @BeforeEach
    internal fun setUp() {
        originalDownloadAsCbz = serverConfig.downloadAsCbz.value
    }

    private fun userWithPermissions(
        userId: Int,
        vararg permissions: UserPermission,
    ): UserType = UserType.User(id = userId, permissions = permissions.toList())

    private fun GraphQLResponse<*>.assertForbidden() {
        assertHasError()
        assertEquals(
            true,
            errors?.any { it.message.contains("Forbidden") },
            "Expected a Forbidden error but got: $errors",
        )
    }

    private fun GraphQLResponse<*>.assertErrorContaining(text: String) {
        assertHasError()
        assertEquals(
            true,
            errors?.any { it.message.contains(text) },
            "Expected an error containing \"$text\" but got: $errors",
        )
    }

    private fun updateUser(
        userId: Int,
        input: Map<String, Any?>,
    ): GraphQLResponse<*> =
        graphql(
            """
            mutation(${'$'}input: UpdateUserInput!) {
                updateUser(input: ${'$'}input) {
                    user {
                        id
                        username
                        roles
                        permissions
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to (mapOf("userId" to userId) + input)),
        )

    @Test
    fun userForbiddenWithoutPermission() {
        val userId = createTestUser("gqluser1")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    user(id: ${'$'}id) {
                        id
                    }
                }
                """.trimIndent(),
                mapOf("id" to userId),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun usersForbiddenWithoutPermission() {
        val userId = createTestUser("gqluser2")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        val response =
            graphql(
                """
                query {
                    users {
                        nodes {
                            id
                        }
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun registerForbiddenWithoutPermission() {
        val userId = createTestUser("gqluser3")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        val response =
            graphql(
                """
                mutation(${'$'}input: RegisterInput!) {
                    register(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("username" to "gqlregister", "password" to "newpass")),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun updateUserForbiddenWithoutPermission() {
        val userId = createTestUser("gqluser4")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateUserInput!) {
                    updateUser(input: ${'$'}input) {
                        user {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("userId" to userId, "role" to "USER")),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun usersReturnsNodeListForAdmin() {
        val userId = createTestUser("gqluser5")
        transaction {
            UserRolesTable.insert {
                it[UserRolesTable.user] = userId
                it[UserRolesTable.role] = UserRole.USER.name
            }
            UserPermissionsTable.insert {
                it[UserPermissionsTable.user] = userId
                it[UserPermissionsTable.permission] = UserPermission.DOWNLOAD_CHAPTERS.name
            }
        }

        val response =
            graphql(
                """
                query {
                    users {
                        nodes {
                            id
                            username
                            roles
                            permissions
                        }
                        edges {
                            cursor
                            node {
                                id
                            }
                        }
                        pageInfo {
                            hasNextPage
                            hasPreviousPage
                        }
                        totalCount
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()

        val nodes = response.dataPath("users", "nodes") as List<*>
        val nodeIds = nodes.map { (it as Map<*, *>)["id"] as Int }

        assertTrue(nodeIds.contains(1), "the built-in admin should be listed")
        assertTrue(nodeIds.contains(userId), "the test user should be listed")
        assertEquals(2, response.dataPath("users", "totalCount") as Int)
        assertEquals(nodes.size, (response.dataPath("users", "edges") as List<*>).size)
        assertNotNull(response.dataPath("users", "pageInfo", "hasNextPage"))

        val adminNode = nodes.first { (it as Map<*, *>)["id"] == 1 } as Map<*, *>
        assertTrue((adminNode["roles"] as List<*>).contains(UserRole.ADMIN.name), "admin should have the ADMIN role")

        val userNode = nodes.first { (it as Map<*, *>)["id"] == userId } as Map<*, *>
        assertEquals("gqluser5", userNode["username"])
        assertEquals(listOf(UserRole.USER.name), userNode["roles"])
        assertEquals(listOf(UserPermission.DOWNLOAD_CHAPTERS.name), userNode["permissions"])
    }

    @Test
    fun userReturnsSingleNode() {
        val userId = createTestUser("gqluser6")
        transaction {
            UserRolesTable.insert {
                it[UserRolesTable.user] = userId
                it[UserRolesTable.role] = UserRole.USER.name
            }
            UserPermissionsTable.batchInsert(UserPermission.defaultPermissions) {
                this[UserPermissionsTable.user] = userId
                this[UserPermissionsTable.permission] = it.name
            }
        }

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    user(id: ${'$'}id) {
                        id
                        username
                        roles
                        permissions
                    }
                }
                """.trimIndent(),
                mapOf("id" to userId),
            )

        response.assertNoErrors()
        assertEquals(userId, response.dataPath("user", "id"))
        assertEquals("gqluser6", response.dataPath("user", "username"))
        assertEquals(listOf(UserRole.USER.name), response.dataPath("user", "roles"))
        assertEquals(
            UserPermission.defaultPermissions.map { it.name }.sorted(),
            (response.dataPath("user", "permissions") as List<*>).map { it as String }.sorted(),
        )
    }

    @Test
    fun userReturnsErrorForUnknownId() {
        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    user(id: ${'$'}id) {
                        id
                    }
                }
                """.trimIndent(),
                mapOf("id" to 999999),
            )

        response.assertHasError()
    }

    @Test
    fun usersConditionRoleAndPermissionCombinedDoesNotFanOut() {
        val userA = createTestUser("fanout_a")
        val userB = createTestUser("fanout_b")

        transaction {
            UserRolesTable.insert {
                it[UserRolesTable.user] = userA
                it[UserRolesTable.role] = UserRole.USER.name
            }
            UserPermissionsTable.insert {
                it[UserPermissionsTable.user] = userA
                it[UserPermissionsTable.permission] = UserPermission.MANAGE_CACHE.name
            }

            // user B has two permission rows: a join would return it twice
            UserRolesTable.insert {
                it[UserRolesTable.user] = userB
                it[UserRolesTable.role] = UserRole.USER.name
            }
            UserPermissionsTable.batchInsert(listOf(UserPermission.DOWNLOAD_CHAPTERS, UserPermission.INSTALL_EXTENSIONS)) {
                this[UserPermissionsTable.user] = userB
                this[UserPermissionsTable.permission] = it.name
            }
        }

        val response =
            graphql(
                """
                query(${'$'}condition: UserConditionInput!) {
                    users(condition: ${'$'}condition) {
                        nodes {
                            id
                        }
                        totalCount
                    }
                }
                """.trimIndent(),
                mapOf("condition" to mapOf("role" to UserRole.USER.name, "permission" to UserPermission.INSTALL_EXTENSIONS.name)),
            )

        response.assertNoErrors()

        val nodeIds = (response.dataPath("users", "nodes") as List<*>).map { (it as Map<*, *>)["id"] as Int }

        assertEquals(
            1,
            response.dataPath("users", "totalCount") as Int,
            "only user B matches; a join on the permission rows would fan out",
        )
        assertTrue(nodeIds.contains(userB))
        assertFalse(nodeIds.contains(userA))
    }

    @Test
    fun usersFilterPermission() {
        val userA = createTestUser("filterperm_a")
        val userB = createTestUser("filterperm_b")

        transaction {
            UserRolesTable.insert {
                it[UserRolesTable.user] = userA
                it[UserRolesTable.role] = UserRole.USER.name
            }
            UserPermissionsTable.insert {
                it[UserPermissionsTable.user] = userA
                it[UserPermissionsTable.permission] = UserPermission.MANAGE_CACHE.name
            }

            UserRolesTable.insert {
                it[UserRolesTable.user] = userB
                it[UserRolesTable.role] = UserRole.USER.name
            }
            UserPermissionsTable.insert {
                it[UserPermissionsTable.user] = userB
                it[UserPermissionsTable.permission] = UserPermission.DOWNLOAD_CHAPTERS.name
            }
        }

        val response =
            graphql(
                """
                query(${'$'}filter: UserFilterInput!) {
                    users(filter: ${'$'}filter) {
                        nodes {
                            id
                        }
                        totalCount
                    }
                }
                """.trimIndent(),
                mapOf("filter" to mapOf("permission" to mapOf("equalTo" to UserPermission.MANAGE_CACHE.name))),
            )

        response.assertNoErrors()

        val nodeIds = (response.dataPath("users", "nodes") as List<*>).map { (it as Map<*, *>)["id"] as Int }

        assertEquals(
            1,
            response.dataPath("users", "totalCount") as Int,
        )
        assertTrue(nodeIds.contains(userA))
        assertFalse(nodeIds.contains(userB))
    }

    @Test
    fun usersPagination() {
        repeat(3) { i ->
            val userId = createTestUser("page_$i")
            transaction {
                UserRolesTable.insert {
                    it[UserRolesTable.user] = userId
                    it[UserRolesTable.role] = UserRole.USER.name
                }
            }
        }

        val page1 =
            graphql(
                """
                query {
                    users(first: 2) {
                        nodes {
                            id
                        }
                        pageInfo {
                            hasNextPage
                            endCursor
                        }
                        totalCount
                    }
                }
                """.trimIndent(),
            )

        page1.assertNoErrors()
        assertEquals(2, (page1.dataPath("users", "nodes") as List<*>).size)
        assertEquals(4, page1.dataPath("users", "totalCount") as Int, "admin + 3 test users")
        assertEquals(true, page1.dataPath("users", "pageInfo", "hasNextPage"))

        val endCursor = page1.dataPath("users", "pageInfo", "endCursor") as String

        val page2 =
            graphql(
                """
                query(${'$'}after: Cursor!) {
                    users(first: 2, after: ${'$'}after) {
                        nodes {
                            id
                        }
                        pageInfo {
                            hasNextPage
                        }
                    }
                }
                """.trimIndent(),
                mapOf("after" to endCursor),
            )

        page2.assertNoErrors()
        assertEquals(2, (page2.dataPath("users", "nodes") as List<*>).size)
        assertEquals(false, page2.dataPath("users", "pageInfo", "hasNextPage"))

        val page1Ids = (page1.dataPath("users", "nodes") as List<*>).map { (it as Map<*, *>)["id"] as Int }
        val page2Ids = (page2.dataPath("users", "nodes") as List<*>).map { (it as Map<*, *>)["id"] as Int }
        assertEquals(0, (page1Ids intersect page2Ids).size, "pages must not overlap")
    }

    @Test
    fun updateUserReplacesPermissionsAndRole() {
        val userId = createTestUser("updateuser1")
        transaction {
            UserRolesTable.insert {
                it[UserRolesTable.user] = userId
                it[UserRolesTable.role] = UserRole.USER.name
            }
            UserPermissionsTable.batchInsert(UserPermission.defaultPermissions) {
                this[UserPermissionsTable.user] = userId
                this[UserPermissionsTable.permission] = it.name
            }
        }

        val response =
            updateUser(
                userId,
                mapOf(
                    "permissions" to listOf(UserPermission.MANAGE_SETTINGS.name, UserPermission.MANAGE_CACHE.name),
                    "role" to UserRole.ADMIN.name,
                ),
            )

        response.assertNoErrors()
        assertEquals(userId, response.dataPath("updateUser", "user", "id"))
        assertEquals(
            listOf(UserPermission.MANAGE_CACHE.name, UserPermission.MANAGE_SETTINGS.name).sorted(),
            (response.dataPath("updateUser", "user", "permissions") as List<*>).map { it as String }.sorted(),
        )
        assertEquals(listOf(UserRole.ADMIN.name), response.dataPath("updateUser", "user", "roles"))

        // the database must reflect the replacement, not a merge
        val (permissions, roles) =
            transaction {
                val userPermissions =
                    UserPermissionsTable
                        .selectAll()
                        .where { UserPermissionsTable.user eq userId }
                        .map { it[UserPermissionsTable.permission] }
                        .toSet()
                val userRoles =
                    UserRolesTable
                        .selectAll()
                        .where { UserRolesTable.user eq userId }
                        .map { it[UserRolesTable.role] }
                        .toSet()
                userPermissions to userRoles
            }

        assertEquals(setOf(UserPermission.MANAGE_SETTINGS.name, UserPermission.MANAGE_CACHE.name), permissions)
        assertEquals(setOf(UserRole.ADMIN.name), roles)
    }

    @Test
    fun updateUserRejectsBuiltInAdmin() {
        val response = updateUser(1, mapOf("role" to UserRole.USER))

        response.assertErrorContaining("The built-in admin user cannot be modified")
    }

    @Test
    fun updateUserRejectsVisitorRole() {
        val userId = createTestUser("updateuser2")

        val response = updateUser(userId, mapOf("role" to UserRole.VISITOR))

        response.assertErrorContaining("The VISITOR role cannot be granted")
    }

    @Test
    fun updateUserRejectsUnknownRole() {
        val userId = createTestUser("updateuser3")

        val response = updateUser(userId, mapOf("role" to "bogus"))

        response.assertHasError()
    }

    @Test
    fun grantFlowAdminGrantsAndRevokesManageSettings() {
        val userId = createTestUser("grantflow1")
        transaction {
            UserRolesTable.insert {
                it[UserRolesTable.user] = userId
                it[UserRolesTable.role] = UserRole.USER.name
            }
        }

        // admin grants MANAGE_SETTINGS
        val grant = updateUser(userId, mapOf("permissions" to listOf(UserPermission.MANAGE_SETTINGS.name)))
        grant.assertNoErrors()
        assertEquals(listOf(UserPermission.MANAGE_SETTINGS.name), grant.dataPath("updateUser", "user", "permissions"))

        val grantedUser = userWithPermissions(userId, UserPermission.MANAGE_SETTINGS)

        // the granted user can now read and write the global settings
        val settings =
            graphql(
                """
                query {
                    settings {
                        authMode
                    }
                }
                """.trimIndent(),
                user = grantedUser,
            )
        settings.assertNoErrors()

        val setSettings =
            graphql(
                """
                mutation(${'$'}input: SetSettingsInput!) {
                    setSettings(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("settings" to mapOf("downloadAsCbz" to !originalDownloadAsCbz))),
                user = grantedUser,
            )
        setSettings.assertNoErrors()
        assertEquals(!originalDownloadAsCbz, serverConfig.downloadAsCbz.value)

        // admin revokes the permission
        val revoke = updateUser(userId, mapOf("permissions" to emptyList<String>()))
        revoke.assertNoErrors()
        assertEquals(emptyList<Any?>(), revoke.dataPath("updateUser", "user", "permissions"))

        // the user no longer has access to the real values; the query succeeds with a
        // masked view and the mutation is a no-op for the global config
        val revokedUser = userWithPermissions(userId)
        val settingsRevoked =
            graphql(
                """
                query {
                    settings {
                        downloadAsCbz
                    }
                }
                """.trimIndent(),
                user = revokedUser,
            )
        settingsRevoked.assertNoErrors()
        assertEquals(
            SettingsRegistry.get("downloadAsCbz")!!.defaultValue,
            settingsRevoked.dataPath("settings", "downloadAsCbz"),
            "the revoked user should see the masked default value",
        )

        val setSettingsRevoked =
            graphql(
                """
                mutation(${'$'}input: SetSettingsInput!) {
                    setSettings(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("settings" to mapOf("downloadAsCbz" to originalDownloadAsCbz))),
                user = revokedUser,
            )
        setSettingsRevoked.assertNoErrors()
        assertEquals(
            !originalDownloadAsCbz,
            serverConfig.downloadAsCbz.value,
            "the global setting must not be changed by the revoked user",
        )
    }

    @AfterEach
    internal fun tearDown() {
        serverConfig.downloadAsCbz.value = originalDownloadAsCbz

        transaction {
            UserPermissionsTable.deleteWhere { UserPermissionsTable.user neq 1 }
            UserRolesTable.deleteWhere { UserRolesTable.user neq 1 }
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }
}
