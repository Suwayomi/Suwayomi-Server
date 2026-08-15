package suwayomi.tachidesk.graphql.dataLoaders

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga

class ChapterDataLoaderTest : ApplicationTest() {
    @AfterEach
    fun cleanup() {
        clearTables(ChapterTable, MangaTable)
    }

    private fun insertChapter(
        mangaId: Int,
        name: String,
        sourceOrder: Int,
        read: Boolean = false,
        downloaded: Boolean = false,
        bookmarked: Boolean = false,
        chapterNumber: Float = sourceOrder.toFloat(),
        lastReadAt: Long = 0L,
        fetchedAt: Long = 0L,
        dateUpload: Long = 0L,
    ): Int =
        transaction {
            ChapterTable.batchInsert(listOf(1)) {
                this[ChapterTable.url] = "ch-$mangaId-$sourceOrder"
                this[ChapterTable.name] = name
                this[ChapterTable.sourceOrder] = sourceOrder
                this[ChapterTable.isRead] = read
                this[ChapterTable.isDownloaded] = downloaded
                this[ChapterTable.isBookmarked] = bookmarked
                this[ChapterTable.manga] = mangaId
                this[ChapterTable.chapter_number] = chapterNumber
                this[ChapterTable.lastReadAt] = lastReadAt
                this[ChapterTable.fetchedAt] = fetchedAt
                this[ChapterTable.date_upload] = dateUpload
                this[ChapterTable.memo] = JsonObject.EMPTY
            }.first()[ChapterTable.id].value
        }

    // -- firstChapterPerManga tests --

    @Test
    fun `returns empty map for empty manga ids`() {
        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = emptyList(),
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }
        assertEquals(emptyMap<Int, Any>(), result)
    }

    @Test
    fun `returns one chapter per manga ordered by sourceOrder ASC`() {
        val manga1 = createLibraryManga("Manga 1")
        val manga2 = createLibraryManga("Manga 2")
        insertChapter(manga1, "Ch 1", sourceOrder = 1)
        insertChapter(manga1, "Ch 2", sourceOrder = 2)
        insertChapter(manga1, "Ch 3", sourceOrder = 3)
        insertChapter(manga2, "Ch 1", sourceOrder = 1)
        insertChapter(manga2, "Ch 2", sourceOrder = 2)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1, manga2),
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertEquals(2, result.size)
        assertEquals("Ch 1", result[manga1]?.name)
        assertEquals("Ch 1", result[manga2]?.name)
    }

    @Test
    fun `returns one chapter per manga ordered by sourceOrder DESC`() {
        val manga1 = createLibraryManga("Manga 1")
        insertChapter(manga1, "Ch 1", sourceOrder = 1)
        insertChapter(manga1, "Ch 2", sourceOrder = 2)
        insertChapter(manga1, "Ch 3", sourceOrder = 3)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.DESC),
                )
            }

        assertEquals("Ch 3", result[manga1]?.name)
    }

    @Test
    fun `filter limits which chapters are considered`() {
        val manga1 = createLibraryManga("Manga 1")
        insertChapter(manga1, "Ch 1 unread", sourceOrder = 1, read = false)
        insertChapter(manga1, "Ch 2 read", sourceOrder = 2, read = true)
        insertChapter(manga1, "Ch 3 read", sourceOrder = 3, read = true)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    filter = ChapterTable.isRead eq true,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        // should skip Ch 1 (unread) and return Ch 2 as first read chapter
        assertEquals("Ch 2 read", result[manga1]?.name)
    }

    @Test
    fun `returns null for manga with no matching chapters`() {
        val manga1 = createLibraryManga("Manga 1")
        insertChapter(manga1, "Ch 1", sourceOrder = 1, read = false)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    filter = ChapterTable.isRead eq true,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertNull(result[manga1])
    }

    @Test
    fun `returns null for manga with no chapters`() {
        val manga1 = createLibraryManga("Manga 1")

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertNull(result[manga1])
    }

    // -- Simulates LastReadChapterForMangaDataLoader --

    @Test
    fun `lastRead - returns chapter with most recent lastReadAt`() {
        val manga1 = createLibraryManga("Manga 1")
        insertChapter(manga1, "Ch 1", sourceOrder = 1, lastReadAt = 100L)
        insertChapter(manga1, "Ch 2", sourceOrder = 2, lastReadAt = 300L)
        insertChapter(manga1, "Ch 3", sourceOrder = 3, lastReadAt = 200L)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    orderBy = listOf(ChapterTable.lastReadAt to SortOrder.DESC),
                )
            }

        assertEquals("Ch 2", result[manga1]?.name)
    }

    // -- Simulates LatestReadChapterForMangaDataLoader --

    @Test
    fun `latestRead - returns read chapter with highest sourceOrder`() {
        val manga1 = createLibraryManga("Manga 1")
        insertChapter(manga1, "Ch 1", sourceOrder = 1, read = true)
        insertChapter(manga1, "Ch 2", sourceOrder = 2, read = true)
        insertChapter(manga1, "Ch 3", sourceOrder = 3, read = false)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    filter = ChapterTable.isRead eq true,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.DESC),
                )
            }

        assertEquals("Ch 2", result[manga1]?.name)
    }

    // -- Simulates FirstUnreadChapterForMangaDataLoader --

    @Test
    fun `firstUnread - returns unread chapter with lowest sourceOrder`() {
        val manga1 = createLibraryManga("Manga 1")
        insertChapter(manga1, "Ch 1", sourceOrder = 1, read = true)
        insertChapter(manga1, "Ch 2", sourceOrder = 2, read = false)
        insertChapter(manga1, "Ch 3", sourceOrder = 3, read = false)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    filter = ChapterTable.isRead eq false,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertEquals("Ch 2", result[manga1]?.name)
    }

    // -- Simulates LatestFetchedChapterForMangaDataLoader --

    @Test
    fun `latestFetched - returns chapter with most recent fetchedAt`() {
        val manga1 = createLibraryManga("Manga 1")
        insertChapter(manga1, "Ch 1", sourceOrder = 1, fetchedAt = 100L)
        insertChapter(manga1, "Ch 2", sourceOrder = 2, fetchedAt = 300L)
        insertChapter(manga1, "Ch 3", sourceOrder = 3, fetchedAt = 300L)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    orderBy = listOf(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC),
                )
            }

        // Same fetchedAt → tiebreak by sourceOrder DESC → Ch 3
        assertEquals("Ch 3", result[manga1]?.name)
    }

    // -- Simulates LatestUploadedChapterForMangaDataLoader --

    @Test
    fun `latestUploaded - returns chapter with most recent dateUpload`() {
        val manga1 = createLibraryManga("Manga 1")
        insertChapter(manga1, "Ch 1", sourceOrder = 1, dateUpload = 500L)
        insertChapter(manga1, "Ch 2", sourceOrder = 2, dateUpload = 100L)
        insertChapter(manga1, "Ch 3", sourceOrder = 3, dateUpload = 300L)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    orderBy = listOf(ChapterTable.date_upload to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC),
                )
            }

        assertEquals("Ch 1", result[manga1]?.name)
    }

    // -- Simulates HighestNumberedChapterForMangaDataLoader --

    @Test
    fun `highestNumbered - returns chapter with highest chapterNumber above 0`() {
        val manga1 = createLibraryManga("Manga 1")
        insertChapter(manga1, "Ch 1", sourceOrder = 1, chapterNumber = 1f)
        insertChapter(manga1, "Ch 2", sourceOrder = 2, chapterNumber = 50f)
        insertChapter(manga1, "Ch 3", sourceOrder = 3, chapterNumber = 25f)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1),
                    filter = ChapterTable.chapter_number greater 0f,
                    orderBy = listOf(ChapterTable.chapter_number to SortOrder.DESC_NULLS_LAST),
                )
            }

        assertEquals("Ch 2", result[manga1]?.name)
    }

    // -- Multi-manga batch test --

    @Test
    fun `batch - correctly partitions results across multiple manga`() {
        val manga1 = createLibraryManga("Manga 1")
        val manga2 = createLibraryManga("Manga 2")
        val manga3 = createLibraryManga("Manga 3")
        insertChapter(manga1, "M1-Ch1", sourceOrder = 1, read = false)
        insertChapter(manga1, "M1-Ch2", sourceOrder = 2, read = true)
        insertChapter(manga2, "M2-Ch1", sourceOrder = 1, read = true)
        insertChapter(manga2, "M2-Ch2", sourceOrder = 2, read = false)
        // manga3 has no unread chapters
        insertChapter(manga3, "M3-Ch1", sourceOrder = 1, read = true)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(manga1, manga2, manga3),
                    filter = ChapterTable.isRead eq false,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertEquals("M1-Ch1", result[manga1]?.name)
        assertEquals("M2-Ch2", result[manga2]?.name)
        assertNull(result[manga3])
    }
}
