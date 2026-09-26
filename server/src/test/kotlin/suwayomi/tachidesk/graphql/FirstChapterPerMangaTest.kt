package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.graphql.dataLoaders.firstChapterPerManga
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirstChapterPerMangaTest : ApplicationTest() {
    private lateinit var mangaIds: List<Int>

    @BeforeEach
    fun seed() {
        // A: mixed values with ties; B: never read, all at defaults; C: no chapters.
        val a = createLibraryManga("A")
        val b = createLibraryManga("B")
        val c = createLibraryManga("C")
        createChapters(a, 6, read = false)
        createChapters(b, 4, read = false)
        transaction {
            ChapterTable.selectAll().where { ChapterTable.manga eq a }.forEach { row ->
                val order = row[ChapterTable.sourceOrder]
                ChapterTable.update({ ChapterTable.id eq row[ChapterTable.id] }) {
                    it[isRead] = order <= 3
                    it[lastReadAt] = if (order <= 3) 100L * (order % 2 + 1) else 0L
                    it[fetchedAt] = 50L * (order % 3)
                    it[date_upload] = if (order == 5) 900L else 10L * order
                    it[chapter_number] = if (order == 6) -1f else order.toFloat()
                }
            }
        }
        mangaIds = listOf(a, b, c)
    }

    /** The first chapter id per manga, the way the loaders used to find it. */
    private fun loadAllAndPick(
        order: List<Pair<Expression<*>, SortOrder>>,
        where: Op<Boolean>?,
    ): Map<Int, Int> =
        transaction {
            ChapterTable
                .selectAll()
                .where {
                    val inMangas = ChapterTable.manga inList mangaIds
                    if (where == null) inMangas else inMangas and where
                }.orderBy(*(order + (ChapterTable.id to SortOrder.ASC)).toTypedArray())
                .groupBy { it[ChapterTable.manga].value }
                .mapValues { (_, rows) -> rows.first()[ChapterTable.id].value }
        }

    private fun assertSameAsLoadingAll(
        order: List<Pair<Expression<*>, SortOrder>>,
        where: Op<Boolean>? = null,
    ) {
        val ranked =
            transaction {
                firstChapterPerManga(mangaIds, order, where).mapValues { it.value.id }
            }
        assertEquals(loadAllAndPick(order, where), ranked)
    }

    @Test
    fun `last read`() = assertSameAsLoadingAll(listOf(ChapterTable.lastReadAt to SortOrder.DESC))

    @Test
    fun `latest read`() = assertSameAsLoadingAll(listOf(ChapterTable.sourceOrder to SortOrder.DESC), ChapterTable.isRead eq true)

    @Test
    fun `latest fetched`() =
        assertSameAsLoadingAll(listOf(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC))

    @Test
    fun `latest uploaded`() =
        assertSameAsLoadingAll(listOf(ChapterTable.date_upload to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC))

    @Test
    fun `first unread`() = assertSameAsLoadingAll(listOf(ChapterTable.sourceOrder to SortOrder.ASC), ChapterTable.isRead eq false)

    @Test
    fun `highest numbered`() =
        assertSameAsLoadingAll(
            listOf(ChapterTable.chapter_number to SortOrder.DESC_NULLS_LAST),
            ChapterTable.chapter_number greater 0f,
        )

    @Test
    fun `a manga without matching chapters is absent`() {
        val ranked =
            transaction {
                firstChapterPerManga(mangaIds, listOf(ChapterTable.sourceOrder to SortOrder.ASC))
            }
        assertEquals(setOf(mangaIds[0], mangaIds[1]), ranked.keys)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(ChapterTable, MangaTable)
    }
}
