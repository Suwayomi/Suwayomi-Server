package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.graphql.mutations.ChapterMutation
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChapterTest : ApplicationTest() {
    @Test
    fun `marking a chapter unread clears its history and progress`() {
        val mangaId = createLibraryManga("SINGLE_UNREAD_TEST")
        val chapterId = createReadChapter(mangaId, 1)

        Chapter.modifyChapter(
            mangaId = mangaId,
            chapterIndex = 1,
            isRead = false,
            isBookmarked = null,
            markPrevRead = null,
            lastPageRead = null,
        )

        assertUnreadWithNoProgress(chapterId)
    }

    @Test
    fun `batch marking chapters unread clears their history and progress`() =
        runTest {
            val mangaId = createLibraryManga("BATCH_UNREAD_TEST")
            val chapterIds = listOf(createReadChapter(mangaId, 1), createReadChapter(mangaId, 2))

            Chapter.modifyChapters(
                Chapter.MangaChapterBatchEditInput(
                    chapterIds = chapterIds,
                    change = Chapter.ChapterChange(isRead = false),
                ),
            )

            chapterIds.forEach(::assertUnreadWithNoProgress)
        }

    @Test
    fun `marking previous chapters unread clears their history and progress`() {
        val mangaId = createLibraryManga("PREVIOUS_UNREAD_TEST")
        val previousChapterIds = listOf(createReadChapter(mangaId, 1), createReadChapter(mangaId, 2))
        val currentChapterId = createReadChapter(mangaId, 3)

        Chapter.modifyChapter(
            mangaId = mangaId,
            chapterIndex = 3,
            isRead = null,
            isBookmarked = null,
            markPrevRead = false,
            lastPageRead = null,
        )

        previousChapterIds.forEach(::assertUnreadWithNoProgress)
        assertReadWithProgress(currentChapterId)
    }

    @Test
    fun `graphql unread status takes precedence over a simultaneous progress update`() {
        val mangaId = createLibraryManga("GRAPHQL_UNREAD_TEST")
        val chapterId = createReadChapter(mangaId, 1)

        ChapterMutation().updateChapter(
            ChapterMutation.UpdateChapterInput(
                id = chapterId,
                patch =
                    ChapterMutation.UpdateChapterPatch(
                        isRead = false,
                        lastPageRead = 5,
                    ),
            ),
        )

        assertUnreadWithNoProgress(chapterId)
    }

    private fun createReadChapter(
        mangaId: Int,
        chapterIndex: Int,
    ): Int =
        transaction {
            ChapterTable
                .insertAndGetId {
                    it[url] = chapterIndex.toString()
                    it[name] = chapterIndex.toString()
                    it[sourceOrder] = chapterIndex
                    it[isRead] = true
                    it[lastPageRead] = 7
                    it[lastReadAt] = 123L
                    it[pageCount] = 10
                    it[manga] = mangaId
                    it[memo] = JsonObject.EMPTY
                }.value
        }

    private fun assertUnreadWithNoProgress(chapterId: Int) {
        transaction {
            val chapter = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()
            assertEquals(false, chapter[ChapterTable.isRead])
            assertEquals(0, chapter[ChapterTable.lastPageRead])
            assertEquals(0L, chapter[ChapterTable.lastReadAt])
        }
    }

    private fun assertReadWithProgress(chapterId: Int) {
        transaction {
            val chapter = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()
            assertEquals(true, chapter[ChapterTable.isRead])
            assertEquals(7, chapter[ChapterTable.lastPageRead])
            assertEquals(123L, chapter[ChapterTable.lastReadAt])
        }
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            ChapterTable,
            MangaTable,
        )
    }
}
