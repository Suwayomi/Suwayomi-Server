package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryMutationTest : GraphQLTest() {
    @Test
    fun createCategory() {
        val response =
            graphql(
                """
                mutation(${'$'}input: CreateCategoryInput!) {
                    createCategory(input: ${'$'}input) {
                        category {
                            id
                            name
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("name" to "New Cat")),
            )

        response.assertNoErrors()
        assertEquals("New Cat", response.dataPath("createCategory", "category", "name"))
    }

    @Test
    fun updateCategory() {
        val createResponse =
            graphql(
                """
                mutation(${'$'}input: CreateCategoryInput!) {
                    createCategory(input: ${'$'}input) {
                        category {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("name" to "Old Name")),
            )
        val categoryId = createResponse.dataPath("createCategory", "category", "id") as Int

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateCategoryInput!) {
                    updateCategory(input: ${'$'}input) {
                        category {
                            id
                            name
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to categoryId, "patch" to mapOf("name" to "New Name"))),
            )

        response.assertNoErrors()
        assertEquals("New Name", response.dataPath("updateCategory", "category", "name"))
    }

    @Test
    fun updateCategories() {
        val cat1 = createCategoryViaMutation("Cat 1")
        val cat2 = createCategoryViaMutation("Cat 2")

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateCategoriesInput!) {
                    updateCategories(input: ${'$'}input) {
                        categories {
                            id
                            name
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("ids" to listOf(cat1, cat2), "patch" to mapOf("name" to "Renamed"))),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("updateCategories", "categories") as List<*>).size)
    }

    @Test
    fun updateCategoryOrder() {
        val cat1 = createCategoryViaMutation("Cat 1")
        val cat2 = createCategoryViaMutation("Cat 2")

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateCategoryOrderInput!) {
                    updateCategoryOrder(input: ${'$'}input) {
                        categories {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to cat1, "position" to 1)),
            )

        response.assertNoErrors()
        // both categories should still be present
        assertEquals(2, (response.dataPath("updateCategoryOrder", "categories") as List<*>).size)
        assertEquals(true, cat2 in (response.dataPath("updateCategoryOrder", "categories") as List<*>).map { (it as Map<*, *>)["id"] })
    }

    @Test
    fun deleteCategory() {
        val categoryId = createCategoryViaMutation("To Delete")

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteCategoryInput!) {
                    deleteCategory(input: ${'$'}input) {
                        category {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("categoryId" to categoryId)),
            )

        response.assertNoErrors()
        assertEquals(categoryId, response.dataPath("deleteCategory", "category", "id"))
    }

    @Test
    fun setCategoryMeta() {
        val categoryId = createCategoryViaMutation("Meta Cat")

        val response =
            graphql(
                """
                mutation(${'$'}input: SetCategoryMetaInput!) {
                    setCategoryMeta(input: ${'$'}input) {
                        meta {
                            key
                            value
                            categoryId
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("meta" to mapOf("key" to "catKey", "value" to "catValue", "categoryId" to categoryId))),
            )

        response.assertNoErrors()
        assertEquals("catKey", response.dataPath("setCategoryMeta", "meta", "key"))
        assertEquals(categoryId, response.dataPath("setCategoryMeta", "meta", "categoryId"))
    }

    @Test
    fun deleteCategoryMeta() {
        val categoryId = createCategoryViaMutation("Meta Cat")
        graphql(
            """
            mutation(${'$'}input: SetCategoryMetaInput!) {
                setCategoryMeta(input: ${'$'}input) {
                    meta {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("meta" to mapOf("key" to "catKey", "value" to "catValue", "categoryId" to categoryId))),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteCategoryMetaInput!) {
                    deleteCategoryMeta(input: ${'$'}input) {
                        meta {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("categoryId" to categoryId, "key" to "catKey")),
            )

        response.assertNoErrors()
        assertEquals("catKey", response.dataPath("deleteCategoryMeta", "meta", "key"))
    }

    @Test
    fun setCategoryMetas() {
        val categoryId = createCategoryViaMutation("Meta Cat")

        val response =
            graphql(
                """
                mutation(${'$'}input: SetCategoryMetasInput!) {
                    setCategoryMetas(input: ${'$'}input) {
                        metas {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf(
                    "input" to
                        mapOf(
                            "items" to
                                listOf(
                                    mapOf(
                                        "categoryIds" to listOf(categoryId),
                                        "metas" to
                                            listOf(
                                                mapOf("key" to "cmk1", "value" to "cmv1"),
                                                mapOf("key" to "cmk2", "value" to "cmv2"),
                                            ),
                                    ),
                                ),
                        ),
                ),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("setCategoryMetas", "metas") as List<*>).size)
    }

    @Test
    fun deleteCategoryMetas() {
        val categoryId = createCategoryViaMutation("Meta Cat")
        graphql(
            """
            mutation(${'$'}input: SetCategoryMetasInput!) {
                setCategoryMetas(input: ${'$'}input) {
                    metas {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf(
                "input" to
                    mapOf(
                        "items" to
                            listOf(
                                mapOf(
                                    "categoryIds" to listOf(categoryId),
                                    "metas" to
                                        listOf(
                                            mapOf("key" to "cmk1", "value" to "cmv1"),
                                            mapOf("key" to "cmk2", "value" to "cmv2"),
                                        ),
                                ),
                            ),
                    ),
            ),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteCategoryMetasInput!) {
                    deleteCategoryMetas(input: ${'$'}input) {
                        metas {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("items" to listOf(mapOf("categoryIds" to listOf(categoryId), "keys" to listOf("cmk1", "cmk2"))))),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("deleteCategoryMetas", "metas") as List<*>).size)
    }

    @Test
    fun updateMangaCategories() {
        val mangaId = createLibraryManga("Manga")
        val categoryId = createCategoryViaMutation("Manga Cat")

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateMangaCategoriesInput!) {
                    updateMangaCategories(input: ${'$'}input) {
                        manga {
                            id
                            categories {
                                nodes {
                                    id
                                }
                            }
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to mangaId, "patch" to mapOf("addToCategories" to listOf(categoryId)))),
            )

        response.assertNoErrors()
        assertEquals(mangaId, response.dataPath("updateMangaCategories", "manga", "id"))
    }

    @Test
    fun updateMangasCategories() {
        val mangaId1 = createLibraryManga("Manga 1")
        val mangaId2 = createLibraryManga("Manga 2")
        val categoryId = createCategoryViaMutation("Manga Cat")

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateMangasCategoriesInput!) {
                    updateMangasCategories(input: ${'$'}input) {
                        mangas {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("ids" to listOf(mangaId1, mangaId2), "patch" to mapOf("addToCategories" to listOf(categoryId)))),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("updateMangasCategories", "mangas") as List<*>).size)
    }

    @Test
    fun updateCategoriesIncludeInDownloadIsPerUser() {
        val userA = createTestUser("catdl_a")
        val userB = createTestUser("catdl_b")
        val userAType = UserType.User(id = userA, permissions = emptyList())
        val userBType = UserType.User(id = userB, permissions = emptyList())

        val catA = createCategoryAs("Cat A", userAType)
        val catB = createCategoryAs("Cat B", userBType)

        // user A patches includeInDownload on both categories, including user B's
        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateCategoriesInput!) {
                    updateCategories(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf(
                    "input" to mapOf("ids" to listOf(catA, catB), "patch" to mapOf("includeInDownload" to IncludeOrExclude.INCLUDE.name)),
                ),
                user = userAType,
            )

        response.assertNoErrors()

        val (includeA, includeB) =
            transaction {
                val a = CategoryTable.selectAll().where { CategoryTable.id eq catA }.first()[CategoryTable.includeInDownload]
                val b = CategoryTable.selectAll().where { CategoryTable.id eq catB }.first()[CategoryTable.includeInDownload]
                a to b
            }

        assertEquals(IncludeOrExclude.INCLUDE.value, includeA, "user A's own category should be updated")
        assertEquals(IncludeOrExclude.UNSET.value, includeB, "user B's category must not be touched by user A")
    }

    private fun createCategoryAs(
        name: String,
        user: UserType,
    ): Int {
        val response =
            graphql(
                """
                mutation(${'$'}input: CreateCategoryInput!) {
                    createCategory(input: ${'$'}input) {
                        category {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("name" to name)),
                user = user,
            )
        response.assertNoErrors()
        return response.dataPath("createCategory", "category", "id") as Int
    }

    private fun createCategoryViaMutation(name: String): Int {
        val response =
            graphql(
                """
                mutation(${'$'}input: CreateCategoryInput!) {
                    createCategory(input: ${'$'}input) {
                        category {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("name" to name)),
            )
        response.assertNoErrors()
        return response.dataPath("createCategory", "category", "id") as Int
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            CategoryMetaTable,
            CategoryMangaTable,
            CategoryTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )

        transaction {
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }
}
