package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.Category
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
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * List queries only compute the total and the first/last rows when the caller selects
 * `totalCount` or `pageInfo.hasNextPage`/`hasPreviousPage`, so these have to keep their values
 * when they are selected, and the nodes have to come back when they aren't.
 */
class PaginationInfoQueryTest : GraphQLTest() {
    @Test
    fun chaptersPageInfoWhenSelected() {
        createChapters(createLibraryManga("Manga"), 5, read = false)

        val response =
            graphql(
                """
                query {
                    chapters(first: 2) {
                        totalCount
                        pageInfo {
                            hasNextPage
                            hasPreviousPage
                        }
                        nodes {
                            id
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(5, response.dataPath("chapters", "totalCount"))
        assertEquals(true, response.dataPath("chapters", "pageInfo", "hasNextPage"))
        assertEquals(false, response.dataPath("chapters", "pageInfo", "hasPreviousPage"))
        assertEquals(2, (response.dataPath("chapters", "nodes") as List<*>).size)
    }

    @Test
    fun chaptersPageInfoSelectedThroughFragmentAndDirective() {
        createChapters(createLibraryManga("Manga"), 5, read = false)

        val response =
            graphql(
                """
                query(${'$'}withTotal: Boolean!) {
                    chapters(first: 2) {
                        totalCount @include(if: ${'$'}withTotal)
                        ...PageInfoFields
                    }
                }

                fragment PageInfoFields on ChapterNodeList {
                    pageInfo {
                        hasNextPage
                    }
                }
                """.trimIndent(),
                mapOf("withTotal" to true),
            )

        response.assertNoErrors()
        assertEquals(5, response.dataPath("chapters", "totalCount"))
        assertEquals(true, response.dataPath("chapters", "pageInfo", "hasNextPage"))
    }

    @Test
    fun chaptersNodesWithoutPageInfo() {
        createChapters(createLibraryManga("Manga"), 5, read = false)

        val response =
            graphql(
                """
                query {
                    chapters(first: 2) {
                        nodes {
                            id
                        }
                        edges {
                            cursor
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("chapters", "nodes") as List<*>).size)
        assertEquals(2, (response.dataPath("chapters", "edges") as List<*>).size)
    }

    @Test
    fun categoriesPageInfoWhenSelected() {
        Category.createCategory(1, "First")
        Category.createCategory(1, "Second")
        val expectedTotal =
            transaction {
                CategoryTable
                    .selectAll()
                    .where { CategoryTable.user eq 1 }
                    .count()
                    .toInt()
            }

        val response =
            graphql(
                """
                query {
                    categories(first: 1) {
                        totalCount
                        pageInfo {
                            hasNextPage
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(expectedTotal, response.dataPath("categories", "totalCount"))
        assertEquals(true, response.dataPath("categories", "pageInfo", "hasNextPage"))
    }

    @Test
    fun extensionStoresPageInfoWhenSelected() {
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

        val response =
            graphql(
                """
                query {
                    extensionStores(first: 1) {
                        totalCount
                        pageInfo {
                            hasNextPage
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(expectedTotal, response.dataPath("extensionStores", "totalCount"))
        assertEquals(true, response.dataPath("extensionStores", "pageInfo", "hasNextPage"))
    }

    /**
     * The default `extensionStores` order (INDEX_URL) must emit composite `id-value` cursors;
     * a bare value cursor throws when `after:` parses it back.
     */
    @Test
    fun extensionStoresDefaultOrderPaginatesByCursor() {
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
        // may include stores seeded outside this test; the default order is INDEX_URL ASC
        val expectedUrls =
            transaction {
                ExtensionStoreTable
                    .selectAll()
                    .orderBy(ExtensionStoreTable.indexUrl to SortOrder.ASC)
                    .map { it[ExtensionStoreTable.indexUrl] }
            }

        val visited = mutableListOf<String>()
        var after: String? = null
        var pages = 0
        while (true) {
            val afterArg = after?.let { """, after: "$it"""" } ?: ""
            val response =
                graphql(
                    """
                    query {
                        extensionStores(first: 2$afterArg) {
                            pageInfo {
                                hasNextPage
                                endCursor
                            }
                            nodes {
                                indexUrl
                            }
                        }
                    }
                    """.trimIndent(),
                )

            response.assertNoErrors()
            visited +=
                (response.dataPath("extensionStores", "nodes") as List<*>)
                    .map { (it as Map<*, *>)["indexUrl"] as String }
            if (response.dataPath("extensionStores", "pageInfo", "hasNextPage") != true) break
            val endCursor =
                response.dataPath("extensionStores", "pageInfo", "endCursor") as String?
                    ?: fail("hasNextPage=true but endCursor is null — the cursor cannot advance")
            assertTrue(
                endCursor.substringBefore('-').toIntOrNull() != null,
                "cursor must be '<id>-<value>' but was '$endCursor'",
            )
            after = endCursor
            if (++pages > 50) fail("cursor pagination did not terminate after 50 pages")
        }

        assertEquals(expectedUrls, visited, "default-order cursor pagination must visit every store exactly once")
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(ChapterTable, MangaTable)
        transaction {
            ExtensionStoreTable.deleteWhere { ExtensionStoreTable.indexUrl like "$TEST_STORE_PREFIX%" }
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
        }
    }

    private companion object {
        const val TEST_STORE_PREFIX = "https://pagination-test.example/store"
    }
}
