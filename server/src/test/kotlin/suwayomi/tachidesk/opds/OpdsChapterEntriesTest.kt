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
import suwayomi.tachidesk.server.settings.set
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The chapter entries resolve their titles from the chapter data and only
 * carry the progress attributes the streaming extension defines.
 */
class OpdsChapterEntriesTest : ApplicationTest() {
    private val baseUrl = "/api/opds/v1.2"

    /**
     * A library manga of the local source with the given chapter names and
     * numbers, each with [pageCount] pages, the first one read up to
     * [lastPageRead].
     */
    private fun createManga(
        chapters: List<Pair<String, Float>>,
        lastPageRead: Int = 0,
        pageCount: Int = 12,
    ): Int =
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
                    .batchInsert(chapters.withIndex()) { (index, chapter) ->
                        this[ChapterTable.url] = "manga/$index"
                        this[ChapterTable.name] = chapter.first
                        this[ChapterTable.chapter_number] = chapter.second
                        this[ChapterTable.sourceOrder] = index + 1
                        this[ChapterTable.pageCount] = pageCount
                        this[ChapterTable.manga] = mangaId
                        this[ChapterTable.memo] = JsonObject.EMPTY
                    }.map { it[ChapterTable.id].value }
            ChapterUserTable.insert {
                it[chapter] = chapterIds.first()
                it[user] = 1
                it[ChapterUserTable.lastPageRead] = lastPageRead
                it[lastReadAt] = 100
            }
            mangaId
        }

    private fun getSeriesChaptersFeed(mangaId: Int) =
        runBlocking {
            OpdsFeedBuilder.getSeriesChaptersFeed(1, baseUrl, Locale.ENGLISH, mangaId, 1, "number_asc", "all")
        }

    @Test
    fun blankNamesFallBackToTheChapterNumber() {
        val mangaId = createManga(listOf("" to 20.5f, " " to 0f, "" to -1f, "第1話　& 外伝" to 1f))

        val feed = getSeriesChaptersFeed(mangaId)

        assertTrue("<title>[○] Chapter 20.5</title>" in feed)
        assertTrue("<title>[○] Chapter 0</title>" in feed)
        // Chapters without a number fall back to their position
        assertTrue("<title>[○] Chapter 3</title>" in feed)
        assertTrue("<title>[○] 第1話　&amp; 外伝</title>" in feed)
    }

    @Test
    fun oneshotWithoutNameIsTitledAsSuch() {
        val mangaId = createManga(listOf("" to -1f))

        val feed = getSeriesChaptersFeed(mangaId)

        assertTrue("<title>[○] Oneshot</title>" in feed)
    }

    @Test
    fun streamLinksOmitLastReadOnTheFirstPage() {
        val mangaId = createManga(listOf("Chapter 1" to 1f))
        userConfig.opdsSkipChapterMetadataFeed.set(1, true)

        val feed = getSeriesChaptersFeed(mangaId)

        assertTrue(OpdsConstants.LINK_REL_PSE_STREAM in feed)
        assertFalse("lastRead=" in feed)
    }

    @Test
    fun historyEntriesPrefixTheSeriesTitle() {
        createManga(listOf("Chapter 1" to 1f), lastPageRead = 3)

        val feed = runBlocking { OpdsFeedBuilder.getHistoryFeed(1, baseUrl, Locale.ENGLISH, 1) }

        assertTrue("<title>[◐] Manga: Chapter 1</title>" in feed)
    }

    private fun getChapterMetadataFeed(mangaId: Int) =
        runBlocking {
            OpdsFeedBuilder.getChapterMetadataFeed(1, baseUrl, Locale.ENGLISH, mangaId, 1)
        }

    @Test
    fun metadataFeedStreamsTheKnownPages() {
        val mangaId = createManga(listOf("Chapter 1" to 1f), lastPageRead = 3)

        val feed = getChapterMetadataFeed(mangaId)

        assertTrue("<title>[◐] Chapter 1</title>" in feed)
        assertTrue("lastRead=\"4\"" in feed)
        assertTrue("/manga/$mangaId/chapter/1/page/0?opds=true" in feed)
    }

    /**
     * The local source has no files for the chapter, so fetching its page list
     * fails like an unreachable source would.
     */
    @Test
    fun metadataFeedKeepsTheChapterWhenTheSourceFails() {
        val mangaId = createManga(listOf("Chapter 1" to 1f), lastPageRead = 3, pageCount = -1)

        val feed = getChapterMetadataFeed(mangaId)

        assertTrue("<title>[◐] Chapter 1</title>" in feed)
        assertFalse(OpdsConstants.LINK_REL_PSE_STREAM in feed)
    }

    @AfterEach
    internal fun tearDown() {
        userConfig.opdsSkipChapterMetadataFeed.set(1, false)
        clearTables(ChapterTable, MangaTable)
    }
}
