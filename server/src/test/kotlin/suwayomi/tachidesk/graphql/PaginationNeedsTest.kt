package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.graphql.queries.ChapterQuery
import suwayomi.tachidesk.graphql.server.primitives.PaginationInfo
import suwayomi.tachidesk.graphql.server.primitives.PaginationNeeds
import suwayomi.tachidesk.graphql.server.primitives.applySortAndGetPaginationInfo
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaginationNeedsTest : ApplicationTest() {
    private val sort by lazy {
        listOf(
            ChapterQuery.ChapterOrder(ChapterQuery.ChapterOrderBy.SOURCE_ORDER, SortOrder.ASC),
            ChapterQuery.ChapterOrder(ChapterQuery.ChapterOrderBy.ID, SortOrder.ASC),
        )
    }

    /** The pagination info, and the SQL statements it took to compute it. */
    private fun paginate(needs: PaginationNeeds): Pair<PaginationInfo<Int>, List<String>> {
        val mangaId = createLibraryManga("Paginated")
        createChapters(mangaId, 5, read = false)
        val statements = mutableListOf<String>()
        val info =
            transaction {
                addLogger(
                    object : SqlLogger {
                        override fun log(
                            context: StatementContext,
                            transaction: Transaction,
                        ) {
                            statements += context.sql(transaction)
                        }
                    },
                )
                ChapterTable
                    .selectAll()
                    .applySortAndGetPaginationInfo(sort, null, null, ChapterTable.id, needs)
            }
        return info to statements
    }

    @Test
    fun `a caller selecting neither total nor bounds runs no extra query`() {
        val (info, statements) = paginate(PaginationNeeds(total = false, bounds = false))
        assertEquals(emptyList<String>(), statements)
        assertEquals(0, info.total)
        assertNull(info.firstResult)
        assertNull(info.lastResult)
    }

    @Test
    fun `a caller selecting everything still gets the total and bounds`() {
        val (info, statements) = paginate(PaginationNeeds.ALL)
        assertEquals(3, statements.size)
        assertEquals(5, info.total)
        val ids = transaction { ChapterTable.selectAll().map { it[ChapterTable.id].value }.sorted() }
        assertEquals(ids.first(), info.firstResult)
        assertEquals(ids.last(), info.lastResult)
    }

    @Test
    fun `the total alone runs only the count`() {
        val (info, statements) = paginate(PaginationNeeds(total = true, bounds = false))
        assertEquals(1, statements.size)
        assertEquals(5, info.total)
        assertNull(info.firstResult)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(ChapterTable, MangaTable)
    }
}
