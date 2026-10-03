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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.graphql.server.primitives.firstRowPerPartition
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables

/** [firstRowPerPartition] isn't tied to chapters: here, the alphabetically last manga of each source. */
class FirstRowPerPartitionTest : ApplicationTest() {
    private fun insertManga(
        title: String,
        sourceId: Long,
    ): Int =
        MangaTable
            .insertAndGetId {
                it[MangaTable.title] = title
                it[url] = "$sourceId/$title"
                it[sourceReference] = sourceId
            }.value

    @Test
    fun `picks the first row of each partition in a single query`() {
        val (expected, sources) =
            transaction {
                insertManga("Apple", SOURCE_A)
                val lastOfA = insertManga("Zebra", SOURCE_A)
                insertManga("Mango", SOURCE_A)
                val onlyOfB = insertManga("Kiwi", SOURCE_B)
                mapOf(SOURCE_A to lastOfA, SOURCE_B to onlyOfB) to listOf(SOURCE_A, SOURCE_B, SOURCE_WITHOUT_MANGA)
            }

        val statements = mutableListOf<String>()
        val firstBySource =
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
                MangaTable
                    .firstRowPerPartition(
                        partitionBy = MangaTable.sourceReference,
                        idColumn = MangaTable.id,
                        orderBy = listOf(MangaTable.title to SortOrder.DESC),
                        where = MangaTable.sourceReference inList sources,
                    ).associate { it[MangaTable.sourceReference] to it[MangaTable.id].value }
            }

        assertEquals(expected, firstBySource)
        assertEquals(1, statements.size, "expected a single query, ran $statements")
    }

    @Test
    fun `ties go to the lowest id`() {
        val firstId =
            transaction {
                val first = insertManga("Same", SOURCE_A)
                insertManga("Same", SOURCE_A)
                first
            }

        val firstBySource =
            transaction {
                MangaTable
                    .firstRowPerPartition(
                        partitionBy = MangaTable.sourceReference,
                        idColumn = MangaTable.id,
                        orderBy = listOf(MangaTable.title to SortOrder.ASC),
                        where = MangaTable.sourceReference eq SOURCE_A,
                    ).map { it[MangaTable.id].value }
            }

        assertEquals(listOf(firstId), firstBySource)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(MangaTable)
    }

    private companion object {
        const val SOURCE_A = 4_000_001L
        const val SOURCE_B = 4_000_002L
        const val SOURCE_WITHOUT_MANGA = 4_000_003L
    }
}
