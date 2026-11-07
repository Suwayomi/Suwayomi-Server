package suwayomi.tachidesk.opds

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.opds.dto.OpdsMangaFilter
import suwayomi.tachidesk.opds.impl.OpdsFeedBuilder
import suwayomi.tachidesk.opds.repository.ChapterRepository
import suwayomi.tachidesk.opds.repository.MangaRepository
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpdsLibraryQueriesTest : ApplicationTest() {
    /**
     * Creates a library manga with [chapterCount] chapters, the first
     * [readCount] of them read and the first [downloadedCount] downloaded.
     */
    private fun createLibraryManga(
        title: String,
        status: MangaStatus,
        chapterCount: Int,
        readCount: Int,
        downloadedCount: Int,
    ): Int =
        transaction {
            val mangaId =
                MangaTable
                    .insertAndGetId {
                        it[MangaTable.title] = title
                        it[url] = title
                        it[sourceReference] = LocalSource.ID
                        it[MangaTable.status] = status.value
                    }.value
            MangaUserTable.insert {
                it[manga] = mangaId
                it[user] = 1
                it[inLibrary] = true
            }
            val chapterIds =
                ChapterTable
                    .batchInsert(1..chapterCount) {
                        this[ChapterTable.url] = "$mangaId-$it"
                        this[ChapterTable.name] = "$it"
                        this[ChapterTable.sourceOrder] = it
                        this[ChapterTable.manga] = mangaId
                        this[ChapterTable.memo] = JsonObject.EMPTY
                    }.map { it[ChapterTable.id].value }
            ChapterUserTable.batchInsert(chapterIds.take(maxOf(readCount, downloadedCount)).withIndex()) { (index, chapterId) ->
                this[ChapterUserTable.chapter] = chapterId
                this[ChapterUserTable.user] = 1
                this[ChapterUserTable.isRead] = index < readCount
                this[ChapterUserTable.isDownloaded] = index < downloadedCount
            }
            mangaId
        }

    @Test
    fun chapterFilterCountsTreatMissingUserRowsAsUnread() {
        val mangaId = createLibraryManga("A", MangaStatus.ONGOING, chapterCount = 5, readCount = 2, downloadedCount = 0)

        val counts = ChapterRepository.getChapterFilterCounts(1, mangaId)

        assertEquals(mapOf("read" to 2L, "unread" to 3L, "all" to 5L), counts)
    }

    @Test
    fun libraryFilterCountsCountEachMangaOnce() {
        createLibraryManga("A", MangaStatus.ONGOING, chapterCount = 3, readCount = 1, downloadedCount = 2)
        createLibraryManga("B", MangaStatus.COMPLETED, chapterCount = 2, readCount = 2, downloadedCount = 1)
        createLibraryManga("C", MangaStatus.COMPLETED, chapterCount = 1, readCount = 0, downloadedCount = 0)

        val counts = MangaRepository.getLibraryFilterCounts(1, OpdsMangaFilter())

        assertEquals(mapOf("unread" to 2L, "downloaded" to 2L, "ongoing" to 1L, "completed" to 2L), counts)
    }

    @Test
    fun libraryMangaIsSortedAndCountedPerManga() {
        val a = createLibraryManga("A", MangaStatus.ONGOING, chapterCount = 3, readCount = 3, downloadedCount = 0)
        val b = createLibraryManga("B", MangaStatus.ONGOING, chapterCount = 4, readCount = 0, downloadedCount = 0)
        val c = createLibraryManga("C", MangaStatus.COMPLETED, chapterCount = 2, readCount = 1, downloadedCount = 0)

        val byTitle = MangaRepository.getLibraryManga(1, OpdsMangaFilter(), 1, "alpha_desc", null)
        assertEquals(3L, byTitle.totalCount)
        assertEquals(listOf(c, b, a), byTitle.mangaEntries.map { it.id })

        val byUnread = MangaRepository.getLibraryManga(1, OpdsMangaFilter(), 1, "unread_desc", null)
        assertEquals(3L, byUnread.totalCount)
        assertEquals(listOf(b, c, a), byUnread.mangaEntries.map { it.id })

        val unreadOnly = MangaRepository.getLibraryManga(1, OpdsMangaFilter(filter = "unread"), 1, "unread_desc", "unread")
        assertEquals(2L, unreadOnly.totalCount)
        assertEquals(listOf(b, c), unreadOnly.mangaEntries.map { it.id })
    }

    @Test
    fun onlyActiveFacetsAreMarked() {
        createLibraryManga("A", MangaStatus.ONGOING, chapterCount = 1, readCount = 0, downloadedCount = 0)

        val feed = OpdsFeedBuilder.getLibraryFeed(1, "/api/opds/v1.2", Locale.ENGLISH, OpdsMangaFilter(), false, 1, null, null)

        assertTrue("activeFacet=\"true\"" in feed)
        assertFalse("activeFacet=\"false\"" in feed)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(ChapterTable, MangaTable)
    }
}
