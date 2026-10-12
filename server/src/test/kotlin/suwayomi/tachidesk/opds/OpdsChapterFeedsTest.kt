package suwayomi.tachidesk.opds

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.impl.OpdsFeedBuilder
import suwayomi.tachidesk.opds.repository.ChapterRepository
import suwayomi.tachidesk.server.settings.set
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The chapter feeds read the user's progress through a left join on
 * [ChapterUserTable], which has no row for chapters never opened.
 */
class OpdsChapterFeedsTest : ApplicationTest() {
    /**
     * A library manga with a chapter of 12 pages read up to the 4th one, and a
     * chapter without user row nor known page count.
     */
    private fun createMangaWithChapters(): Pair<Int, List<Int>> =
        transaction {
            val mangaId =
                MangaTable
                    .insertAndGetId {
                        it[title] = "Manga"
                        it[url] = "manga"
                        it[sourceReference] = LocalSource.ID
                    }.value
            MangaUserTable.insert {
                it[manga] = mangaId
                it[user] = 1
                it[inLibrary] = true
            }
            val chapterIds =
                ChapterTable
                    .batchInsert(listOf(1 to 12, 2 to -1)) { (sourceOrder, pageCount) ->
                        this[ChapterTable.url] = "$mangaId-$sourceOrder"
                        this[ChapterTable.name] = "Chapter $sourceOrder"
                        this[ChapterTable.sourceOrder] = sourceOrder
                        this[ChapterTable.pageCount] = pageCount
                        this[ChapterTable.manga] = mangaId
                        this[ChapterTable.memo] = JsonObject.EMPTY
                    }.map { it[ChapterTable.id].value }
            ChapterUserTable.insert {
                it[chapter] = chapterIds.first()
                it[user] = 1
                it[lastPageRead] = 3
                it[lastReadAt] = 100
            }
            mangaId to chapterIds
        }

    @Test
    fun libraryUpdatesIncludeUserProgress() {
        val (_, chapterIds) = createMangaWithChapters()

        val (items, total) = ChapterRepository.getLibraryUpdates(1, 1)

        assertEquals(2L, total)
        val chapters = items.associate { it.chapter.id to it.chapter }
        assertEquals(3, chapters.getValue(chapterIds.first()).lastPageRead)
        assertEquals(0, chapters.getValue(chapterIds.last()).lastPageRead)
        assertEquals(false, chapters.getValue(chapterIds.last()).read)
        assertEquals(2L, items.first().manga.totalChapters)
    }

    @Test
    fun historyIncludesUserProgress() {
        val (_, chapterIds) = createMangaWithChapters()

        val (items, total) = ChapterRepository.getHistory(1, 1)

        assertEquals(1L, total)
        assertEquals(chapterIds.first(), items.single().chapter.id)
        assertEquals(3, items.single().chapter.lastPageRead)
        assertEquals(100L, items.single().chapter.lastReadAt)
    }

    @Test
    fun seriesChaptersIncludeChaptersWithoutUserRow() {
        val (mangaId, chapterIds) = createMangaWithChapters()

        val (chapters, total) =
            runBlocking {
                ChapterRepository.getChaptersForManga(1, mangaId, ChapterTable.sourceOrder, SortOrder.ASC, "all", 1)
            }

        assertEquals(2L, total)
        assertEquals(chapterIds, chapters.map { it.id })
        assertEquals(false, chapters.last().downloaded)
    }

    @Test
    fun chapterEntriesOnlyLinkToTheirMetadataFeed() {
        val (mangaId, _) = createMangaWithChapters()

        val feed =
            runBlocking {
                OpdsFeedBuilder.getSeriesChaptersFeed(1, "/api/opds/v1.2", Locale.ENGLISH, mangaId, 1, "number_asc", "all")
            }

        assertFalse(OpdsConstants.LINK_REL_PSE_STREAM in feed)
        assertTrue("/chapter/1/metadata" in feed)
    }

    @Test
    fun chapterEntriesIncludeKnownLinksAndMetadataLink() {
        val (mangaId, _) = createMangaWithChapters()
        userConfig.opdsSkipChapterMetadataFeed.set(1, true)

        val feed =
            runBlocking {
                OpdsFeedBuilder.getSeriesChaptersFeed(1, "/api/opds/v1.2", Locale.ENGLISH, mangaId, 1, "number_asc", "all")
            }
        val (knownChapter, unknownChapter) = feed.split("<entry").drop(1)

        assertTrue("<title>[◐] Chapter 1</title>" in knownChapter)
        assertTrue(OpdsConstants.LINK_REL_PSE_STREAM in knownChapter)
        assertTrue("lastRead=\"4\"" in knownChapter)
        assertFalse("/page/0" in knownChapter)
        assertTrue("/chapter/1/metadata" in knownChapter)

        assertTrue("<title>[○] Chapter 2</title>" in unknownChapter)
        assertFalse(OpdsConstants.LINK_REL_PSE_STREAM in unknownChapter)
        assertTrue("/chapter/2/metadata" in unknownChapter)
    }

    @AfterEach
    internal fun tearDown() {
        userConfig.opdsSkipChapterMetadataFeed.set(1, false)
        clearTables(ChapterTable, MangaTable)
    }
}
