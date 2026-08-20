package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables

class CategoryQueryTest : GraphQLTest() {
    private val user2: UserType = UserType.Admin(2)

    @Test
    fun categoryReturnsCategoryForOwner() {
        val categoryId = Category.createCategory(1, "Owner Cat")

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    category(id: ${'$'}id) {
                        id
                        name
                    }
                }
                """.trimIndent(),
                mapOf("id" to categoryId),
            )

        response.assertNoErrors()
        assertEquals(categoryId, response.dataPath("category", "id"))
        assertEquals("Owner Cat", response.dataPath("category", "name"))
    }

    @Test
    fun categoryIsNullForOtherUser() {
        val categoryId = Category.createCategory(1, "Isolated Cat")

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    category(id: ${'$'}id) {
                        id
                    }
                }
                """.trimIndent(),
                mapOf("id" to categoryId),
                user = user2,
            )

        // error is expected since single item queries error when not usable
        response.assertHasError()
    }

    @Test
    fun categoriesListsOwnCategories() {
        Category.createCategory(1, "Cat A")
        Category.createCategory(1, "Cat B")

        val response =
            graphql(
                """
                query {
                    categories(filter: { name: { notIn: ["Default"] } }) {
                        nodes {
                            name
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        val names = (response.dataPath("categories", "nodes") as List<*>).map { (it as Map<*, *>)["name"] }
        assertEquals(listOf("Cat A", "Cat B"), names)
    }

    @Test
    fun categoriesIsIsolatedPerUser() {
        val user2Id = createTestUser("user2")
        val user2 = UserType.Admin(user2Id)
        Category.createCategory(1, "User1Only")
        Category.createCategory(user2Id, "User2Only")

        val user1Response =
            graphql(
                """
                query {
                    categories {
                        nodes {
                            name
                        }
                    }
                }
                """.trimIndent(),
            )
        val user2Response =
            graphql(
                """
                query {
                    categories {
                        nodes {
                            name
                        }
                    }
                }
                """.trimIndent(),
                user = user2,
            )

        user1Response.assertNoErrors()
        user2Response.assertNoErrors()

        val user1Names = (user1Response.dataPath("categories", "nodes") as List<*>).map { (it as Map<*, *>)["name"] }
        val user2Names = (user2Response.dataPath("categories", "nodes") as List<*>).map { (it as Map<*, *>)["name"] }

        assertEquals(true, "User1Only" in user1Names, "user 1 should see their own category")
        assertEquals(false, "User2Only" in user1Names, "user 1 should not see user 2's category")
        assertEquals(true, "User2Only" in user2Names, "user 2 should see their own category")
        assertEquals(false, "User1Only" in user2Names, "user 2 should not see user 1's category")
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            CategoryMangaTable,
            CategoryTable,
        )
        transaction {
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }
}
