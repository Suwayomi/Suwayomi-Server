package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables

class MetaQueryTest : GraphQLTest() {
    private val user2: UserType = UserType.Admin(2)

    private fun createGlobalMeta(
        key: String,
        value: String,
        user: Int,
    ) {
        transaction {
            GlobalMetaTable.insert {
                it[GlobalMetaTable.key] = key
                it[GlobalMetaTable.value] = value
                it[GlobalMetaTable.user] = user
            }
        }
    }

    @Test
    fun metaReturnsMetaForOwner() {
        createGlobalMeta("ownerKey", "ownerValue", 1)

        val response =
            graphql(
                """
                query(${'$'}key: String!) {
                    meta(key: ${'$'}key) {
                        key
                        value
                    }
                }
                """.trimIndent(),
                mapOf("key" to "ownerKey"),
            )

        response.assertNoErrors()
        assertEquals("ownerKey", response.dataPath("meta", "key"))
        assertEquals("ownerValue", response.dataPath("meta", "value"))
    }

    @Test
    fun metaIsNullForOtherUser() {
        createGlobalMeta("isolatedKey", "isolatedValue", 1)

        val response =
            graphql(
                """
                query(${'$'}key: String!) {
                    meta(key: ${'$'}key) {
                        key
                    }
                }
                """.trimIndent(),
                mapOf("key" to "isolatedKey"),
                user = user2,
            )

        response.assertNoErrors()
        assertEquals(null, response.dataPath("meta"), "meta should be null for a different user")
    }

    @Test
    fun metasListsOwnMetas() {
        createGlobalMeta("key1", "value1", 1)
        createGlobalMeta("key2", "value2", 1)

        val response =
            graphql(
                """
                query {
                    metas {
                        totalCount
                        nodes {
                            key
                            value
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(2, response.dataPath("metas", "totalCount"))
    }

    @Test
    fun metasIsIsolatedPerUser() {
        val user2Id = createTestUser("user2")
        val user2 = UserType.Admin(user2Id)
        createGlobalMeta("user1Key", "user1Value", 1)
        createGlobalMeta("user2Key", "user2Value", user2Id)

        val user1Response =
            graphql(
                """
                query {
                    metas {
                        nodes {
                            key
                        }
                    }
                }
                """.trimIndent(),
            )
        val user2Response =
            graphql(
                """
                query {
                    metas {
                        nodes {
                            key
                        }
                    }
                }
                """.trimIndent(),
                user = user2,
            )

        user1Response.assertNoErrors()
        user2Response.assertNoErrors()

        val user1Keys = (user1Response.dataPath("metas", "nodes") as List<*>).map { (it as Map<*, *>)["key"] }
        val user2Keys = (user2Response.dataPath("metas", "nodes") as List<*>).map { (it as Map<*, *>)["key"] }

        assertEquals(true, "user1Key" in user1Keys, "user 1 should see their own meta")
        assertEquals(false, "user2Key" in user1Keys, "user 1 should not see user 2's meta")
        assertEquals(true, "user2Key" in user2Keys, "user 2 should see their own meta")
        assertEquals(false, "user1Key" in user2Keys, "user 2 should not see user 1's meta")
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(GlobalMetaTable)
        transaction {
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }
}
