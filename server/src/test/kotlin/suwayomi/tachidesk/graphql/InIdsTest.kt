package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.graphql.server.primitives.inIds
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables

class InIdsTest : ApplicationTest() {
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
    fun `selects the same rows as inList with the ids written into the SQL`() {
        transaction {
            val ids = (1..5).map { insertManga("Manga $it", 1L) }
            val wanted = listOf(ids[3], ids[0], ids[3], ids[1])

            val query = MangaTable.selectAll().where { MangaTable.id inIds wanted }
            val sql = query.prepareSQL(this, prepared = true)
            assertFalse('?' in sql, "ids should not be bound: $sql")
            assertTrue(wanted.all { it.toString() in sql }, sql)

            val expected =
                MangaTable
                    .selectAll()
                    .where { MangaTable.id inList wanted }
                    .map { it[MangaTable.id].value }
                    .toSet()
            assertEquals(expected, query.map { it[MangaTable.id].value }.toSet())
        }
    }

    @Test
    fun `combines with other conditions`() {
        transaction {
            val inSourceA = insertManga("A", SOURCE_A)
            val inSourceB = insertManga("B", SOURCE_B)

            val found =
                MangaTable
                    .selectAll()
                    .where { MangaTable.id inIds listOf(inSourceA, inSourceB) and (MangaTable.sourceReference eq SOURCE_B) }
                    .map { it[MangaTable.id].value }
            assertEquals(listOf(inSourceB), found)
        }
    }

    @Test
    fun `matches long ids`() {
        transaction {
            val inSourceA = insertManga("A", SOURCE_A)
            insertManga("B", SOURCE_B)

            val found =
                MangaTable
                    .selectAll()
                    .where { MangaTable.sourceReference inIds listOf(SOURCE_A) }
                    .map { it[MangaTable.id].value }
            assertEquals(listOf(inSourceA), found)
        }
    }

    @Test
    fun `matches nothing for no ids`() {
        transaction {
            insertManga("A", SOURCE_A)
            assertEquals(0L, MangaTable.selectAll().where { MangaTable.id inIds emptyList<Int>() }.count())
        }
    }

    @AfterEach
    fun cleanup() {
        clearTables(MangaTable)
    }

    companion object {
        private const val SOURCE_A = 10_000_000_000L
        private const val SOURCE_B = 10_000_000_001L
    }
}
