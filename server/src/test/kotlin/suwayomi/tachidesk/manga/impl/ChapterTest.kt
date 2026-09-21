package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga

private const val SCANLATOR_A_URL = "scanlator-a/chapter-1"
private const val SCANLATOR_B_URL = "scanlator-b/chapter-1"
private const val SCANLATOR_C_URL = "scanlator-c/chapter-1"

private const val CHAPTER_NAME = "Chapter 1"

/** must not contain anything [eu.kanade.tachiyomi.util.chapter.ChapterRecognition] could parse a chapter number from */
private const val UNRECOGNIZED_CHAPTER_NAME = "Oneshot"

private const val READ_CHAPTER_FETCHED_AT = 1_000L

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChapterTest : ApplicationTest() {
    @BeforeEach
    @AfterEach
    fun cleanup() {
        clearTables(ChapterTable, MangaTable)
    }

    @Test
    fun `duplicate of a read chapter gets marked as read`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = true)

        fetchChapters(mangaId, listOf(createSChapter(SCANLATOR_A_URL), createSChapter(SCANLATOR_B_URL)))

        assertTrue(isRead(SCANLATOR_B_URL), "The duplicate of an already read chapter should have been marked as read")
        assertTrue(isRead(SCANLATOR_A_URL), "The already read chapter should still be read")
        assertEquals(0, unreadCount(mangaId), "The manga should not have any unread chapters")
    }

    @Test
    fun `duplicate of a read chapter keeps the fetch date of the read chapter`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = true)

        fetchChapters(mangaId, listOf(createSChapter(SCANLATOR_A_URL), createSChapter(SCANLATOR_B_URL)))

        assertEquals(
            READ_CHAPTER_FETCHED_AT,
            fetchedAt(SCANLATOR_B_URL),
            "The duplicate should not show up as a new chapter in the 'Updates' tab",
        )
    }

    @Test
    fun `duplicate of a read chapter stays unread while the setting is disabled`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = true)

        fetchChapters(
            mangaId,
            listOf(createSChapter(SCANLATOR_A_URL), createSChapter(SCANLATOR_B_URL)),
            markDuplicateReadChaptersAsRead = false,
        )

        assertFalse(isRead(SCANLATOR_B_URL), "The duplicate should not have been touched while the setting is disabled")
        assertEquals(1, unreadCount(mangaId), "The manga should have one unread chapter")
    }

    @Test
    fun `duplicate of an unread chapter stays unread`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = false)

        fetchChapters(mangaId, listOf(createSChapter(SCANLATOR_A_URL), createSChapter(SCANLATOR_B_URL)))

        assertFalse(isRead(SCANLATOR_B_URL), "The duplicate of an unread chapter should not have been marked as read")
        assertEquals(2, unreadCount(mangaId), "The manga should have two unread chapters")
    }

    @Test
    fun `chapter without a recognized number does not count as a duplicate`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = true, chapterNumber = -1f, chapterName = UNRECOGNIZED_CHAPTER_NAME)

        fetchChapters(
            mangaId,
            listOf(
                createSChapter(SCANLATOR_A_URL, chapterNumber = -1f, chapterName = UNRECOGNIZED_CHAPTER_NAME),
                createSChapter(SCANLATOR_B_URL, chapterNumber = -1f, chapterName = UNRECOGNIZED_CHAPTER_NAME),
            ),
        )

        assertFalse(isRead(SCANLATOR_B_URL), "Chapters without a recognized number should never be treated as duplicates")
    }

    @Test
    fun `read duplicate wins over a deleted unread duplicate`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = true)
        createChapter(mangaId, SCANLATOR_B_URL, read = false)

        // "B" is gone from the source and "C" is new, thus, the unread "B" gets deleted and "C" gets inserted
        fetchChapters(mangaId, listOf(createSChapter(SCANLATOR_A_URL), createSChapter(SCANLATOR_C_URL)))

        assertTrue(isRead(SCANLATOR_C_URL), "The kept read chapter should win over the deleted unread chapter")
    }

    @Test
    fun `duplicate that is already in the database gets marked as read`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = true)
        createChapter(mangaId, SCANLATOR_B_URL, read = false)

        // nothing new gets fetched, both chapters are kept
        fetchChapters(mangaId, listOf(createSChapter(SCANLATOR_A_URL), createSChapter(SCANLATOR_B_URL)))

        assertTrue(isRead(SCANLATOR_B_URL), "A duplicate that predates the setting should have been marked as read")
        assertEquals(0, unreadCount(mangaId), "The manga should not have any unread chapters")
    }

    @Test
    fun `duplicate that is already in the database stays unread while the setting is disabled`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = true)
        createChapter(mangaId, SCANLATOR_B_URL, read = false)

        fetchChapters(
            mangaId,
            listOf(createSChapter(SCANLATOR_A_URL), createSChapter(SCANLATOR_B_URL)),
            markDuplicateReadChaptersAsRead = false,
        )

        assertFalse(isRead(SCANLATOR_B_URL), "The duplicate should not have been touched while the setting is disabled")
    }

    @Test
    fun `read state of a kept chapter is never cleared`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = true, chapterNumber = 1f)
        createChapter(mangaId, SCANLATOR_B_URL, read = true, chapterNumber = 2f, chapterName = "Chapter 2")

        fetchChapters(
            mangaId,
            listOf(
                createSChapter(SCANLATOR_A_URL),
                createSChapter(SCANLATOR_B_URL, chapterNumber = 2f, chapterName = "Chapter 2"),
            ),
        )

        assertTrue(isRead(SCANLATOR_A_URL), "A read chapter without a duplicate should stay read")
        assertTrue(isRead(SCANLATOR_B_URL), "A read chapter without a duplicate should stay read")
    }

    @Test
    fun `read state of a deleted chapter still gets restored`() {
        val mangaId = createLibraryManga("Psyren")
        createChapter(mangaId, SCANLATOR_A_URL, read = true)

        // "A" is gone from the source and "B" is new, thus, the read "A" gets deleted and "B" gets inserted
        fetchChapters(mangaId, listOf(createSChapter(SCANLATOR_B_URL)), markDuplicateReadChaptersAsRead = false)

        assertTrue(isRead(SCANLATOR_B_URL), "The read state of the deleted chapter should have been restored")
    }

    private fun createSChapter(
        chapterUrl: String,
        chapterNumber: Float = 1f,
        chapterName: String = CHAPTER_NAME,
    ): SChapter =
        SChapter.create().apply {
            url = chapterUrl
            name = chapterName
            chapter_number = chapterNumber
        }

    private fun createChapter(
        mangaId: Int,
        chapterUrl: String,
        read: Boolean,
        chapterNumber: Float = 1f,
        chapterName: String = CHAPTER_NAME,
    ) {
        transaction {
            ChapterTable.insert {
                it[url] = chapterUrl
                it[name] = chapterName
                it[chapter_number] = chapterNumber
                it[sourceOrder] = 1
                it[isRead] = read
                it[fetchedAt] = READ_CHAPTER_FETCHED_AT
                it[manga] = mangaId
                it[memo] = JsonObject.EMPTY
            }
        }
    }

    private fun fetchChapters(
        mangaId: Int,
        chapters: List<SChapter>,
        markDuplicateReadChaptersAsRead: Boolean = true,
    ) = runBlocking {
        val mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }

        Chapter.updateChapterListDatabase(mangaEntry, chapters, StubSource(1), markDuplicateReadChaptersAsRead)
    }

    private fun chapter(chapterUrl: String) =
        transaction {
            ChapterTable.selectAll().where { ChapterTable.url eq chapterUrl }.first()
        }

    private fun isRead(chapterUrl: String): Boolean = chapter(chapterUrl)[ChapterTable.isRead]

    private fun fetchedAt(chapterUrl: String): Long = chapter(chapterUrl)[ChapterTable.fetchedAt]

    private fun unreadCount(mangaId: Int): Int =
        transaction {
            ChapterTable
                .selectAll()
                .where { (ChapterTable.manga eq mangaId) and (ChapterTable.isRead eq false) }
                .count()
                .toInt()
        }
}
