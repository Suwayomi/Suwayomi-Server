package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * U3 regression tests for Relay-style pagination: argument validation, forward/backward
 * windows, `last` result ordering, cursor-window bounds and cursor stability over
 * LEFT JOIN'd nullable sort columns.
 */
class PaginationArgsTest : GraphQLTest() {
    private fun nodeIds(
        response: com.expediagroup.graphql.server.types.GraphQLResponse<*>,
        root: String,
    ): List<String> = (response.dataPath(root, "nodes") as List<*>).map { (it as Map<*, *>)["id"].toString() }

    // ---------- argument validation ----------

    @Test
    fun `negative first is rejected`() {
        val response =
            graphql(
                """
                query {
                    chapters(first: -1) {
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        response.assertHasError()
        assertTrue(
            response.errors!!.any { it.message.contains("first must be in 0..") },
            "expected a page-size validation error but got: ${response.errors}",
        )
    }

    @Test
    fun `page size above the maximum is rejected`() {
        val response =
            graphql(
                """
                query {
                    chapters(first: 1001) {
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        response.assertHasError()
        assertTrue(
            response.errors!!.any { it.message.contains("first must be in 0..") },
            "expected a page-size validation error but got: ${response.errors}",
        )
    }

    @Test
    fun `the WebUI page size of 150 is accepted`() {
        // Regression: the stock WebUI pages the updates list in chunks of 150;
        // a cap of 100 broke the updates page for real clients.
        val response =
            graphql(
                """
                query {
                    chapters(first: 150) {
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
    }

    @Test
    fun `first and last together are rejected`() {
        val response =
            graphql(
                """
                query {
                    chapters(first: 1, last: 1) {
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        response.assertHasError()
        assertTrue(
            response.errors!!.any { it.message.contains("mutually exclusive") },
            "expected a mutual-exclusion validation error but got: ${response.errors}",
        )
    }

    @Test
    fun `offset together with last is rejected`() {
        val response =
            graphql(
                """
                query {
                    chapters(last: 1, offset: 1) {
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        response.assertHasError()
        assertTrue(
            response.errors!!.any { it.message.contains("offset with last is not supported") },
            "expected an offset/last validation error but got: ${response.errors}",
        )
    }

    // ---------- backward pagination ----------

    @Test
    fun `last returns the tail of the list in forward order`() {
        val mangaId = createLibraryManga("PaginatedLast")
        createChapters(mangaId, 5, read = false)
        val allIds =
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.id)
                    .map { it[ChapterTable.id].value.toString() }
            }

        val response =
            graphql(
                """
                query {
                    chapters(condition: { mangaId: $mangaId }, last: 2) {
                        pageInfo {
                            hasNextPage
                            hasPreviousPage
                        }
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(allIds.takeLast(2), nodeIds(response, "chapters"), "the tail must stay in forward order")
        assertEquals(false, response.dataPath("chapters", "pageInfo", "hasNextPage"))
        assertEquals(true, response.dataPath("chapters", "pageInfo", "hasPreviousPage"))
    }

    @Test
    fun `before alone does not reverse the page`() {
        val mangaId = createLibraryManga("PaginatedBefore")
        createChapters(mangaId, 5, read = false)
        val allIds =
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.id)
                    .map { it[ChapterTable.id].value.toString() }
            }

        val response =
            graphql(
                """
                query {
                    chapters(condition: { mangaId: $mangaId }, first: 2, before: "${allIds[4]}") {
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(allIds.take(2), nodeIds(response, "chapters"), "before is a bound, not a sort reversal")
    }

    @Test
    fun `after and before together select the window between the cursors`() {
        val mangaId = createLibraryManga("PaginatedWindow")
        createChapters(mangaId, 5, read = false)
        val allIds =
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.id)
                    .map { it[ChapterTable.id].value.toString() }
            }

        val response =
            graphql(
                """
                query {
                    chapters(condition: { mangaId: $mangaId }, after: "${allIds[1]}", before: "${allIds[4]}") {
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(allIds.subList(2, 4), nodeIds(response, "chapters"))
    }

    // ---------- nullable LEFT JOIN sort keys ----------

    @Test
    fun `chapters without a user row are not lost when paging by lastReadAt`() {
        val mangaId = createLibraryManga("PaginatedLastReadAt")
        // bare chapters: no ChapterUserTable row, so lastReadAt is NULL on the LEFT JOIN
        val chapterIds =
            transaction {
                ChapterTable
                    .batchInsert(1..4) {
                        this[ChapterTable.url] = "$it"
                        this[ChapterTable.name] = "$it"
                        this[ChapterTable.sourceOrder] = it
                        this[ChapterTable.manga] = mangaId
                        this[ChapterTable.memo] = JsonObject.EMPTY
                    }.map { it[ChapterTable.id].value.toString() }
            }

        val page1 =
            graphql(
                """
                query {
                    chapters(condition: { mangaId: $mangaId }, order: [{ by: LAST_READ_AT, byType: ASC }], first: 2) {
                        pageInfo {
                            hasNextPage
                            endCursor
                        }
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        page1.assertNoErrors()
        assertEquals(2, (page1.dataPath("chapters", "nodes") as List<*>).size)
        assertEquals(true, page1.dataPath("chapters", "pageInfo", "hasNextPage"))
        val endCursor = page1.dataPath("chapters", "pageInfo", "endCursor") as String
        assertNotNull(endCursor)

        val page2 =
            graphql(
                """
                query {
                    chapters(condition: { mangaId: $mangaId }, order: [{ by: LAST_READ_AT, byType: ASC }], first: 3, after: "$endCursor") {
                        pageInfo {
                            hasNextPage
                        }
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        page2.assertNoErrors()
        val page2Ids = nodeIds(page2, "chapters")
        assertEquals(chapterIds.size - 2, page2Ids.size, "rows with NULL lastReadAt must not drop out of pagination")
        assertEquals(chapterIds.drop(2), page2Ids)
        assertEquals(false, page2.dataPath("chapters", "pageInfo", "hasNextPage"))
    }

    // ---------- extension store cursors ----------

    @Test
    fun `extensionStores second page returns the remaining stores`() {
        transaction {
            (1..3).forEach { index ->
                ExtensionStoreTable.insert {
                    it[indexUrl] = "$TEST_STORE_PREFIX$index/index.min.json"
                    it[name] = "Store $index"
                    it[badgeLabel] = "S$index"
                    it[signingKey] = "key$index"
                    it[contactWebsite] = "https://store$index.example"
                }
            }
        }
        val expectedTotal = transaction { ExtensionStoreTable.selectAll().count().toInt() }

        val page1 =
            graphql(
                """
                query {
                    extensionStores(first: 1) {
                        pageInfo {
                            hasNextPage
                            endCursor
                        }
                        nodes { name }
                    }
                }
                """.trimIndent(),
            )

        page1.assertNoErrors()
        assertEquals(true, page1.dataPath("extensionStores", "pageInfo", "hasNextPage"))
        val endCursor = page1.dataPath("extensionStores", "pageInfo", "endCursor") as String

        val page2 =
            graphql(
                """
                query {
                    extensionStores(first: $expectedTotal, after: "$endCursor") {
                        pageInfo {
                            hasNextPage
                        }
                        nodes { name }
                    }
                }
                """.trimIndent(),
            )

        page2.assertNoErrors()
        val page2Nodes = page2.dataPath("extensionStores", "nodes") as List<*>
        assertEquals(expectedTotal - 1, page2Nodes.size, "the second page must return all remaining stores")
        assertEquals(false, page2.dataPath("extensionStores", "pageInfo", "hasNextPage"))
    }

    // ---------- category negation ----------

    @Test
    fun `categoryId notIn excludes mangas that carry the category`() {
        val inCategoryManga = createLibraryManga("MangaInCategory")
        val plainManga = createLibraryManga("MangaWithoutCategory")
        val categoryId = Category.createCategory(1, "PaginationNotInCategory")
        transaction {
            CategoryMangaTable.insert {
                it[CategoryMangaTable.manga] = inCategoryManga
                it[CategoryMangaTable.category] = categoryId
                it[CategoryMangaTable.user] = 1
            }
        }

        val response =
            graphql(
                """
                query {
                    mangas(condition: { inLibrary: true }, filter: { categoryId: { notIn: [$categoryId] } }) {
                        nodes { id }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        val ids = nodeIds(response, "mangas")
        assertTrue(plainManga.toString() in ids, "a manga without the category must be returned")
        assertTrue(inCategoryManga.toString() !in ids, "a manga carrying the category must be excluded")
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(ChapterTable, MangaTable, CategoryMangaTable)
        transaction {
            ExtensionStoreTable.deleteWhere { ExtensionStoreTable.indexUrl like "$TEST_STORE_PREFIX%" }
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
        }
    }

    private companion object {
        const val TEST_STORE_PREFIX = "https://pagination-args-test.example/store"
    }
}
