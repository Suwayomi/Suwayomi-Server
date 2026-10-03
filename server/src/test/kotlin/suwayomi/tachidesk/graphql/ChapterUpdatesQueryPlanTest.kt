package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertTrue

class ChapterUpdatesQueryPlanTest : ApplicationTest() {
    @AfterEach
    fun cleanup() {
        clearTables(ChapterUserTable, ChapterTable, MangaUserTable, MangaTable)
    }

    // Same shape as ChapterQuery.chapters for the updates list: library chapters ordered by
    // FETCHED_AT DESC, SOURCE_ORDER DESC, then the ID tiebreaker.
    private fun updatesQuery(
        reverse: Boolean,
        userId: Int = 1,
    ): Query {
        val fetchedAtOrder = if (reverse) SortOrder.ASC else SortOrder.DESC
        return ChapterTable
            .getWithUserData(userId)
            .innerJoin(MangaTable.getWithUserData(userId))
            .selectAll()
            .andWhere { MangaUserTable.inLibrary eq true }
            .orderBy(
                ChapterTable.fetchedAt to fetchedAtOrder,
                ChapterTable.sourceOrder to fetchedAtOrder,
                ChapterTable.id to if (reverse) SortOrder.DESC else SortOrder.ASC,
            ).limit(50)
    }

    private fun explain(query: Query): String =
        transaction {
            exec("EXPLAIN " + query.prepareSQL(this, prepared = false), explicitStatementType = StatementType.SELECT) { rs ->
                buildString { while (rs.next()) appendLine(rs.getString(1)) }
            }.orEmpty()
        }

    @Test
    fun updatesPageAndBoundsWalkTheFetchedAtIndex() {
        // The plan text is H2's; Postgres picks a sequential scan on a table this small anyway.
        Assumptions.assumeTrue(serverConfig.databaseType.value == DatabaseType.H2)

        repeat(5) { createChapters(createLibraryManga("Manga $it"), 20, read = false) }

        for (reverse in listOf(false, true)) {
            val plan = explain(updatesQuery(reverse))
            assertTrue("CHAPTER_FETCHED_AT" in plan.uppercase(), "reverse=$reverse plan doesn't use the index:\n$plan")
            assertTrue("index sorted" in plan, "reverse=$reverse plan still sorts the rows:\n$plan")
        }
    }
}
