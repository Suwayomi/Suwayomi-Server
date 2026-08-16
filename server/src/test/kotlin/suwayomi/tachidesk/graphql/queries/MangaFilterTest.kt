package suwayomi.tachidesk.graphql.queries

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.graphql.queries.filter.BooleanFilter
import suwayomi.tachidesk.graphql.queries.filter.applyOps
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga

class MangaFilterTest : ApplicationTest() {
    @AfterEach
    fun cleanup() {
        clearTables(ChapterTable, MangaTable)
    }

    private fun insertChapters(
        mangaId: Int,
        count: Int,
        read: Boolean = false,
        downloaded: Boolean = false,
        bookmarked: Boolean = false,
        chapterNumbers: List<Float>? = null,
    ) {
        transaction {
            ChapterTable.batchInsert((1..count).toList()) { i ->
                this[ChapterTable.url] = "ch-$mangaId-$i"
                this[ChapterTable.name] = "Chapter $i"
                this[ChapterTable.sourceOrder] = i
                this[ChapterTable.isRead] = read
                this[ChapterTable.isDownloaded] = downloaded
                this[ChapterTable.isBookmarked] = bookmarked
                this[ChapterTable.manga] = mangaId
                this[ChapterTable.chapter_number] = chapterNumbers?.getOrNull(i - 1) ?: i.toFloat()
                this[ChapterTable.memo] = JsonObject.EMPTY
            }
        }
    }

    private fun queryMangaIds(filter: MangaQuery.MangaFilter): List<Int> =
        transaction {
            MangaTable
                .selectAll()
                .applyOps(null, filter)
                .map { it[MangaTable.id].value }
                .sorted()
        }

    @Test
    fun `hasUnreadChapters equalTo true returns manga with unread chapters`() {
        val manga1 = createLibraryManga("Manga With Unread")
        val manga2 = createLibraryManga("Manga All Read")
        insertChapters(manga1, 3, read = false)
        insertChapters(manga2, 3, read = true)

        val result = queryMangaIds(MangaQuery.MangaFilter(hasUnreadChapters = BooleanFilter(equalTo = true)))
        assertEquals(listOf(manga1), result)
    }

    @Test
    fun `hasUnreadChapters equalTo false returns manga without unread chapters`() {
        val manga1 = createLibraryManga("Manga With Unread")
        val manga2 = createLibraryManga("Manga All Read")
        insertChapters(manga1, 3, read = false)
        insertChapters(manga2, 3, read = true)

        val result = queryMangaIds(MangaQuery.MangaFilter(hasUnreadChapters = BooleanFilter(equalTo = false)))
        assertEquals(listOf(manga2), result)
    }

    @Test
    fun `hasReadChapters equalTo true returns manga with read chapters`() {
        val manga1 = createLibraryManga("Partially Read")
        val manga2 = createLibraryManga("All Unread")
        insertChapters(manga1, 2, read = true)
        insertChapters(manga2, 2, read = false)

        val result = queryMangaIds(MangaQuery.MangaFilter(hasReadChapters = BooleanFilter(equalTo = true)))
        assertEquals(listOf(manga1), result)
    }

    @Test
    fun `hasDownloadedChapters equalTo true returns manga with downloaded chapters`() {
        val manga1 = createLibraryManga("Has Downloads")
        val manga2 = createLibraryManga("No Downloads")
        insertChapters(manga1, 2, downloaded = true)
        insertChapters(manga2, 2, downloaded = false)

        val result = queryMangaIds(MangaQuery.MangaFilter(hasDownloadedChapters = BooleanFilter(equalTo = true)))
        assertEquals(listOf(manga1), result)
    }

    @Test
    fun `hasDownloadedChapters equalTo false returns manga without downloaded chapters`() {
        val manga1 = createLibraryManga("Has Downloads")
        val manga2 = createLibraryManga("No Downloads")
        insertChapters(manga1, 2, downloaded = true)
        insertChapters(manga2, 2, downloaded = false)

        val result = queryMangaIds(MangaQuery.MangaFilter(hasDownloadedChapters = BooleanFilter(equalTo = false)))
        assertEquals(listOf(manga2), result)
    }

    @Test
    fun `hasBookmarkedChapters equalTo true returns manga with bookmarked chapters`() {
        val manga1 = createLibraryManga("Has Bookmarks")
        val manga2 = createLibraryManga("No Bookmarks")
        insertChapters(manga1, 2, bookmarked = true)
        insertChapters(manga2, 2, bookmarked = false)

        val result = queryMangaIds(MangaQuery.MangaFilter(hasBookmarkedChapters = BooleanFilter(equalTo = true)))
        assertEquals(listOf(manga1), result)
    }

    @Test
    fun `hasDuplicateChapters equalTo true returns manga with duplicate chapter numbers`() {
        val manga1 = createLibraryManga("Has Duplicates")
        val manga2 = createLibraryManga("No Duplicates")
        insertChapters(manga1, 3, chapterNumbers = listOf(1f, 1f, 2f))
        insertChapters(manga2, 3, chapterNumbers = listOf(1f, 2f, 3f))

        val result = queryMangaIds(MangaQuery.MangaFilter(hasDuplicateChapters = BooleanFilter(equalTo = true)))
        assertEquals(listOf(manga1), result)
    }

    @Test
    fun `hasDuplicateChapters equalTo false returns manga without duplicate chapter numbers`() {
        val manga1 = createLibraryManga("Has Duplicates")
        val manga2 = createLibraryManga("No Duplicates")
        insertChapters(manga1, 3, chapterNumbers = listOf(1f, 1f, 2f))
        insertChapters(manga2, 3, chapterNumbers = listOf(1f, 2f, 3f))

        val result = queryMangaIds(MangaQuery.MangaFilter(hasDuplicateChapters = BooleanFilter(equalTo = false)))
        assertEquals(listOf(manga2), result)
    }

    @Test
    fun `null filter returns all manga`() {
        val manga1 = createLibraryManga("Manga 1")
        val manga2 = createLibraryManga("Manga 2")
        insertChapters(manga1, 2, read = true)
        insertChapters(manga2, 2, read = false)

        val result = queryMangaIds(MangaQuery.MangaFilter())
        assertEquals(listOf(manga1, manga2), result)
    }

    @Test
    fun `manga with no chapters is excluded by hasUnreadChapters true`() {
        val manga1 = createLibraryManga("Has Chapters")
        val manga2 = createLibraryManga("No Chapters")
        insertChapters(manga1, 2, read = false)

        val result = queryMangaIds(MangaQuery.MangaFilter(hasUnreadChapters = BooleanFilter(equalTo = true)))
        assertEquals(listOf(manga1), result)
    }

    @Test
    fun `manga with no chapters is included by hasUnreadChapters false`() {
        val manga1 = createLibraryManga("Has Unread")
        val manga2 = createLibraryManga("No Chapters")
        insertChapters(manga1, 2, read = false)

        val result = queryMangaIds(MangaQuery.MangaFilter(hasUnreadChapters = BooleanFilter(equalTo = false)))
        assertEquals(listOf(manga2), result)
    }
}
