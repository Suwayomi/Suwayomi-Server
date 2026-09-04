package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.user.UserCodeService
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the GQL id shadowing of the per-user default category: in GQL the default category
 * is presented and addressed as id 0 regardless of its real DB id.
 */
class CategoryDefaultGqlTest : GraphQLTest() {
    private fun createUser(username: String): Int =
        transaction {
            UserCodeService.createUser(username, "password")
        }

    private fun userTypeOf(userId: Int): UserType = UserType.Admin(userId)

    @Test
    fun `default category returns the users default category`() {
        val userId = createUser("gqlshadow_a")
        val defaultCategory = Category.getDefaultCategoryId(userId)

        val response =
            graphql(
                """
                query {
                    category(id: $defaultCategory) {
                        id
                        name
                        isDefaultCategory
                        default
                    }
                }
                """.trimIndent(),
                user = userTypeOf(userId),
            )

        response.assertNoErrors()
        assertEquals(defaultCategory, response.dataPath("category", "id"))
        assertEquals("Default", response.dataPath("category", "name"))
        assertEquals(true, response.dataPath("category", "isDefaultCategory"))
        assertEquals(true, response.dataPath("category", "default"))
    }

    @Test
    fun `categories list contains the default category with id zero`() {
        val userId = createUser("gqlshadowlist_a")
        val defaultCategory = Category.getDefaultCategoryId(userId)
        createLibraryManga("Uncat", userId)

        val response =
            graphql(
                """
                query {
                    categories {
                        nodes {
                            id
                            name
                            isDefaultCategory
                        }
                    }
                }
                """.trimIndent(),
                user = userTypeOf(userId),
            )

        response.assertNoErrors()
        val nodes = response.dataPath("categories", "nodes") as List<*>
        val defaultNode = nodes.firstOrNull { (it as Map<*, *>)["name"] == "Default" } as Map<*, *>?
        assertEquals(true, defaultNode != null, "the default category should be listed while uncategorized manga exist")
        assertEquals(defaultCategory, defaultNode!!["id"])
        assertEquals(true, defaultNode["isDefaultCategory"])
    }

    @Test
    fun `default category mangas returns the uncategorized library manga`() {
        val userId = createUser("gqlshadowmangas_a")
        val defaultCategory = Category.getDefaultCategoryId(userId)
        val mangaId = createLibraryManga("UncatManga", userId)

        val response =
            graphql(
                """
                query {
                    category(id: $defaultCategory) {
                        mangas {
                            nodes {
                                id
                            }
                        }
                    }
                }
                """.trimIndent(),
                user = userTypeOf(userId),
            )

        response.assertNoErrors()
        val mangaIds = (response.dataPath("category", "mangas", "nodes") as List<*>).map { (it as Map<*, *>)["id"] }
        assertTrue(mangaId in mangaIds, "category(id: 0).mangas should list the user's uncategorized library manga")
    }

    @Test
    fun `update categories with id zero persists the default category toggles`() {
        val userId = createUser("gqlshadowtoggles_a")
        val defaultDbId = Category.getDefaultCategoryId(userId)!!

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateCategoriesInput!) {
                    updateCategories(input: ${'$'}input) {
                        categories {
                            id
                            includeInUpdate
                            includeInDownload
                        }
                    }
                }
                """.trimIndent(),
                mapOf(
                    "input" to
                        mapOf(
                            "ids" to listOf(defaultDbId),
                            "patch" to
                                mapOf(
                                    "includeInUpdate" to IncludeOrExclude.INCLUDE.name,
                                    "includeInDownload" to IncludeOrExclude.INCLUDE.name,
                                ),
                        ),
                ),
                user = userTypeOf(userId),
            )

        response.assertNoErrors()
        val categories = response.dataPath("updateCategories", "categories") as List<*>
        assertEquals(1, categories.size)
        val category = categories.single() as Map<*, *>
        assertEquals(defaultDbId, category["id"])
        assertEquals(IncludeOrExclude.INCLUDE.name, category["includeInUpdate"])
        assertEquals(IncludeOrExclude.INCLUDE.name, category["includeInDownload"])

        // the toggle persisted on the real DB row
        val (includeInUpdate, includeInDownload) =
            transaction {
                val row = CategoryTable.selectAll().where { CategoryTable.id eq defaultDbId }.first()
                row[CategoryTable.includeInUpdate] to row[CategoryTable.includeInDownload]
            }
        assertEquals(IncludeOrExclude.INCLUDE.value, includeInUpdate)
        assertEquals(IncludeOrExclude.INCLUDE.value, includeInDownload)
    }

    @Test
    fun `update category on the default row is protected against rename and landing flag changes`() {
        val userId = createUser("gqlshadowprotect_a")
        val defaultCategory = Category.getDefaultCategoryId(userId)

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateCategoryInput!) {
                    updateCategory(input: ${'$'}input) {
                        category {
                            id
                            name
                            default
                            includeInUpdate
                        }
                    }
                }
                """.trimIndent(),
                mapOf(
                    "input" to
                        mapOf(
                            "id" to defaultCategory,
                            "patch" to
                                mapOf(
                                    "name" to "Renamed",
                                    "default" to false,
                                    "includeInUpdate" to IncludeOrExclude.INCLUDE.name,
                                ),
                        ),
                ),
                user = userTypeOf(userId),
            )

        response.assertNoErrors()
        assertEquals(defaultCategory, response.dataPath("updateCategory", "category", "id"))
        assertEquals("Default", response.dataPath("updateCategory", "category", "name"), "the default category name is protected")
        assertEquals(true, response.dataPath("updateCategory", "category", "default"), "the default category landing flag is protected")
        assertEquals(
            IncludeOrExclude.INCLUDE.name,
            response.dataPath("updateCategory", "category", "includeInUpdate"),
            "the includeInUpdate toggle is updatable",
        )
    }

    @Test
    fun `delete category with id zero is a no-op`() {
        val userId = createUser("gqlshadowdelete_a")

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteCategoryInput!) {
                    deleteCategory(input: ${'$'}input) {
                        category {
                            id
                        }
                        mangas {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("categoryId" to 0)),
                user = userTypeOf(userId),
            )

        response.assertNoErrors()
        assertEquals(null, response.dataPath("deleteCategory", "category"))
        assertEquals(emptyList<Any>(), response.dataPath("deleteCategory", "mangas"))
        assertEquals(
            true,
            Category.getDefaultCategory(userId) != null,
            "the default category row must survive deleteCategory(categoryId: 0)",
        )
    }

    @Test
    fun `update category order with id zero is a no-op`() {
        val userId = createUser("gqlshadoworder_a")
        val defaultCategoryId = Category.getDefaultCategoryId(userId)
        val otherCategoryId = Category.createCategory(userId, "Other")

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateCategoryOrderInput!) {
                    updateCategoryOrder(input: ${'$'}input) {
                        categories {
                            id
                            name
                            order
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to defaultCategoryId, "position" to 1)),
                user = userTypeOf(userId),
            )

        response.assertNoErrors()
        val categories = response.dataPath("updateCategoryOrder", "categories") as List<*>
        val defaultCategory = categories.first { (it as Map<*, *>)["name"] == "Default" } as Map<*, *>
        assertEquals(defaultCategoryId, defaultCategory["id"])
        assertEquals(0, defaultCategory["order"], "the default category must keep order 0")
        // the other category is untouched
        assertEquals(1, (categories.first { (it as Map<*, *>)["name"] == "Other" } as Map<*, *>)["order"])
        assertEquals(otherCategoryId, (categories.first { (it as Map<*, *>)["name"] == "Other" } as Map<*, *>)["id"])
    }

    @Test
    fun `create category named default is rejected`() {
        val userId = createUser("gqlshadowcreate_a")

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
                mapOf("input" to mapOf("name" to "Default")),
                user = userTypeOf(userId),
            )

        response.assertHasError()
    }

    @Test
    fun `register creates a user with a default category row`() {
        val response =
            graphql(
                """
                mutation(${'$'}input: RegisterInput!) {
                    register(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("username" to "gqlregister_a", "password" to "password")),
            )

        response.assertNoErrors()

        val userId =
            transaction {
                UserAccountTable
                    .selectAll()
                    .where { UserAccountTable.username eq "gqlregister_a" }
                    .first()[UserAccountTable.id]
                    .value
            }
        val row = Category.getDefaultCategory(userId)
        assertEquals(true, row != null, "register should create the user's default category row")
        assertEquals("Default", row!!.name)
    }

    @Test
    fun `redeem registration code creates a user with a default category row`() {
        val codeResponse =
            graphql(
                """
                mutation(${'$'}input: CreateRegistrationCodeInput!) {
                    createRegistrationCode(input: ${'$'}input) {
                        code
                    }
                }
                """.trimIndent(),
                mapOf("input" to emptyMap<String, Any>()),
            )
        codeResponse.assertNoErrors()
        val code = codeResponse.dataPath("createRegistrationCode", "code") as String

        val response =
            graphql(
                """
                mutation(${'$'}input: RedeemRegistrationCodeInput!) {
                    redeemRegistrationCode(input: ${'$'}input) {
                        user {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("code" to code, "username" to "gqlredeem_a", "password" to "password")),
            )

        response.assertNoErrors()
        val userId = response.dataPath("redeemRegistrationCode", "user", "id") as Int
        val row = Category.getDefaultCategory(userId)
        assertEquals(true, row != null, "redeemRegistrationCode should create the user's default category row")
        assertEquals("Default", row!!.name)
    }

    @Test
    fun `update status reports the default category`() {
        val userId = createUser("gqlshadowstatus_a")
        val defaultCategory = Category.getDefaultCategoryId(userId)
        createLibraryManga("UncatStatus", userId)

        val updater = Injekt.get<IUpdater>()
        updater.reset()
        updater.addCategoriesToUpdateQueue(userId, Category.getCategoryList(userId), clear = true, forceAll = false)

        val defaultDbId = Category.getDefaultCategoryId(userId)!!
        val deadline = System.currentTimeMillis() + 5_000
        while (updater.getStatus().categoryUpdates.none { it.category.id == defaultDbId } && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        val response =
            graphql(
                """
                query {
                    updateStatus {
                        updatingCategories {
                            categories {
                                nodes {
                                    id
                                    name
                                    isDefaultCategory
                                }
                            }
                        }
                    }
                }
                """.trimIndent(),
                user = userTypeOf(userId),
            )
        updater.reset()

        response.assertNoErrors()
        val nodes = (response.dataPath("updateStatus", "updatingCategories", "categories", "nodes") as List<*>).map { it as Map<*, *> }
        val defaultNode = nodes.firstOrNull { it["name"] == "Default" }
        assertEquals(true, defaultNode != null, "the default category should be part of the update status")
        assertEquals(defaultDbId, defaultNode!!["id"], "the default category must be reported with GQL id 0")
        assertEquals(true, defaultNode["isDefaultCategory"])
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            CategoryMangaTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
        transaction {
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }
}
