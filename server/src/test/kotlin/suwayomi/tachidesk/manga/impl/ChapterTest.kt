package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChapterTest : ApplicationTest() {
    private val source = StubSource(1L)

    @Test
    fun chapterUrlChangeMigratesPerUserStateForAllUsers() =
        runTest {
            val mangaId = createLibraryManga("URL_CHANGE_TEST")
            val userId2 =
                transaction {
                    UserAccountTable
                        .insertAndGetId {
                            it[UserAccountTable.username] = "user2"
                            it[UserAccountTable.password] = "password"
                        }.value
                }

            val chapter2Id =
                transaction {
                    ChapterTable
                        .batchInsert(listOf("1", "2", "3")) { url ->
                            this[ChapterTable.url] = url
                            this[ChapterTable.name] = url
                            this[ChapterTable.chapter_number] = url.toFloat()
                            this[ChapterTable.sourceOrder] = url.toInt()
                            this[ChapterTable.manga] = mangaId
                            this[ChapterTable.memo] = JsonObject.EMPTY
                        }.first { it[ChapterTable.url] == "2" }[ChapterTable.id]
                        .value
                }

            transaction {
                ChapterUserTable.batchInsert(listOf(1, userId2)) { userId ->
                    this[ChapterUserTable.chapter] = chapter2Id
                    this[ChapterUserTable.user] = userId
                    this[ChapterUserTable.isRead] = userId == 1
                    this[ChapterUserTable.isBookmarked] = userId == 1
                    this[ChapterUserTable.lastPageRead] = if (userId == 1) 5 else 2
                    this[ChapterUserTable.lastReadAt] = if (userId == 1) 1000L else 500L
                }
            }

            val mangaEntry =
                transaction {
                    MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
                }

            // chapter 2 changed its url on the source, chapters 1 and 3 are unchanged
            val fetchedChapters =
                listOf("1", "2-new", "3").map { url ->
                    SChapter.create().apply {
                        this.url = url
                        this.name = url.removeSuffix("-new")
                        this.chapter_number = url.removeSuffix("-new").toFloat()
                    }
                }

            Chapter.updateChapterListDatabase(mangaEntry, fetchedChapters, source)

            val chapterUrls =
                transaction {
                    ChapterTable
                        .select(ChapterTable.url)
                        .where { ChapterTable.manga eq mangaId }
                        .map { it[ChapterTable.url] }
                        .toSet()
                }
            assertEquals(setOf("1", "2-new", "3"), chapterUrls)

            val newChapter2Id =
                transaction {
                    ChapterTable
                        .select(ChapterTable.id)
                        .where { (ChapterTable.manga eq mangaId) and (ChapterTable.url eq "2-new") }
                        .single()[ChapterTable.id]
                        .value
                }

            val userStates =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.chapter eq newChapter2Id) and (ChapterUserTable.user inList listOf(1, userId2)) }
                        .associate { it[ChapterUserTable.user].value to it }
                }
            assertEquals(2, userStates.size)

            val user1State = userStates.getValue(1)
            assertTrue(user1State[ChapterUserTable.isRead])
            assertTrue(user1State[ChapterUserTable.isBookmarked])
            assertEquals(5, user1State[ChapterUserTable.lastPageRead])
            assertEquals(1000L, user1State[ChapterUserTable.lastReadAt])
            assertEquals(0L, user1State[ChapterUserTable.version])

            val user2State = userStates.getValue(userId2)
            assertEquals(false, user2State[ChapterUserTable.isRead])
            assertEquals(false, user2State[ChapterUserTable.isBookmarked])
            assertEquals(2, user2State[ChapterUserTable.lastPageRead])
            assertEquals(500L, user2State[ChapterUserTable.lastReadAt])
            assertEquals(0L, user2State[ChapterUserTable.version])
        }

    @Test
    fun chapterUrlChangeDoesNotMigrateUnrecognizedChapterNumbers() =
        runTest {
            val mangaId = createLibraryManga("UNRECOGNIZED_TEST")
            val chapterId =
                transaction {
                    ChapterTable
                        .batchInsert(listOf("special")) {
                            this[ChapterTable.url] = "special"
                            this[ChapterTable.name] = "Special"
                            this[ChapterTable.chapter_number] = -1f
                            this[ChapterTable.sourceOrder] = 1
                            this[ChapterTable.manga] = mangaId
                            this[ChapterTable.memo] = JsonObject.EMPTY
                        }.first()[ChapterTable.id]
                        .value
                }

            transaction {
                ChapterUserTable.batchInsert(listOf(1)) {
                    this[ChapterUserTable.chapter] = chapterId
                    this[ChapterUserTable.user] = 1
                    this[ChapterUserTable.isRead] = true
                }
            }

            val mangaEntry =
                transaction {
                    MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
                }

            val fetchedChapters =
                listOf(
                    SChapter.create().apply {
                        url = "special-new"
                        name = "Special"
                        chapter_number = -1f
                    },
                )

            Chapter.updateChapterListDatabase(mangaEntry, fetchedChapters, source)

            val newChapterId =
                transaction {
                    ChapterTable
                        .select(ChapterTable.id)
                        .where { (ChapterTable.manga eq mangaId) and (ChapterTable.url eq "special-new") }
                        .single()[ChapterTable.id]
                        .value
                }

            val userRows =
                transaction {
                    ChapterUserTable.selectAll().where { ChapterUserTable.chapter eq newChapterId }.count()
                }
            assertEquals(0, userRows)
        }

    @Test
    fun enqueueDownloadMarksRequestAndReturnsChaptersWithoutSharedDownload() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_ENQUEUE_TEST")
            val chapterIds =
                createChaptersForDownloadTest(mangaId, listOf("1", "2"), downloaded = false)

            val returned = DownloadManager.enqueue(1, chapterIds)

            assertEquals(chapterIds.toSet(), returned.toSet())

            val userStates =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.user eq 1) and (ChapterUserTable.chapter inList chapterIds) }
                        .associate { it[ChapterUserTable.chapter].value to it }
                }
            assertEquals(2, userStates.size)
            chapterIds.forEach { chapterId ->
                val state = userStates.getValue(chapterId)
                assertTrue(state[ChapterUserTable.isDownloadRequested])
                assertEquals(false, state[ChapterUserTable.isDownloaded])
            }

            DownloadManager.dequeue(1, chapterIds)
        }

    @Test
    fun enqueueDownloadOfAlreadyDownloadedChapterMarksUserDownloadedImmediately() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_EXISTING_TEST")
            val chapterId =
                createChaptersForDownloadTest(mangaId, listOf("1"), downloaded = true).single()

            val returned = DownloadManager.enqueue(1, listOf(chapterId))

            assertTrue(returned.isEmpty())

            val state =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.user eq 1) and (ChapterUserTable.chapter eq chapterId) }
                        .single()
                }
            assertTrue(state[ChapterUserTable.isDownloadRequested])
            assertTrue(state[ChapterUserTable.isDownloaded])
        }

    @Test
    fun dequeueDownloadClearsCallerIntentForQueuedChapters() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_DEQUEUE_TEST")
            val chapterIds =
                createChaptersForDownloadTest(mangaId, listOf("1", "2"), downloaded = false)

            DownloadManager.enqueue(1, chapterIds)

            DownloadManager.dequeue(1, chapterIds)

            val userStates =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.user eq 1) and (ChapterUserTable.chapter inList chapterIds) }
                        .associate { it[ChapterUserTable.chapter].value to it }
                }
            assertEquals(2, userStates.size)
            chapterIds.forEach { chapterId ->
                val state = userStates.getValue(chapterId)
                assertEquals(false, state[ChapterUserTable.isDownloadRequested])
                assertEquals(false, state[ChapterUserTable.isDownloaded])
            }

            // the shared queue is empty again
            assertTrue(DownloadManager.getStatus().queue.isEmpty())
        }

    @Test
    fun dequeueDownloadKeepsQueuedChapterWhileOtherUserRequestsIt() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_DEQUEUE_SHARED_TEST")
            val userId2 = createSecondUser()
            val chapterIds =
                createChaptersForDownloadTest(mangaId, listOf("1"), downloaded = false)

            DownloadManager.enqueue(1, chapterIds)
            DownloadManager.enqueue(userId2, chapterIds)

            DownloadManager.dequeue(1, chapterIds)

            val states =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.chapter inList chapterIds) and (ChapterUserTable.user inList listOf(1, userId2)) }
                        .associate { it[ChapterUserTable.user].value to it }
                }
            // the caller's intent is cleared
            assertEquals(false, states.getValue(1)[ChapterUserTable.isDownloadRequested])
            // the other user's intent is untouched
            assertTrue(states.getValue(userId2)[ChapterUserTable.isDownloadRequested])
            // the shared queue entry is kept while the other user still requests it
            assertEquals(1, DownloadManager.getStatus().queue.size)

            DownloadManager.dequeue(userId2, chapterIds)

            val statesAfter =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.chapter inList chapterIds) and (ChapterUserTable.user inList listOf(1, userId2)) }
                        .associate { it[ChapterUserTable.user].value to it }
                }
            assertEquals(false, statesAfter.getValue(userId2)[ChapterUserTable.isDownloadRequested])
            // no user requests it anymore, so the chapter is out of the queue
            assertTrue(DownloadManager.getStatus().queue.isEmpty())
        }

    @Test
    fun deleteDownloadedChaptersRemovesSharedDownloadWhenNoUserRequestsIt() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_DELETE_TEST")
            val chapterId =
                createChaptersForDownloadTest(mangaId, listOf("1"), downloaded = true).single()

            transaction {
                ChapterUserTable.batchInsert(listOf(1)) {
                    this[ChapterUserTable.chapter] = chapterId
                    this[ChapterUserTable.user] = 1
                    this[ChapterUserTable.isDownloadRequested] = true
                    this[ChapterUserTable.isDownloaded] = true
                }
            }

            Chapter.deleteDownloadedChapters(1, listOf(chapterId))

            val state =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.user eq 1) and (ChapterUserTable.chapter eq chapterId) }
                        .single()
                }
            assertEquals(false, state[ChapterUserTable.isDownloadRequested])
            assertEquals(false, state[ChapterUserTable.isDownloaded])

            val downloaded =
                transaction {
                    ChapterTable
                        .select(ChapterTable.isDownloaded)
                        .where { ChapterTable.id eq chapterId }
                        .single()[ChapterTable.isDownloaded]
                }
            assertEquals(false, downloaded)
        }

    @Test
    fun deleteDownloadedChaptersKeepsSharedDownloadWhileOtherUserRequestsIt() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_SHARED_TEST")
            val userId2 = createSecondUser()
            val chapterId =
                createChaptersForDownloadTest(mangaId, listOf("1"), downloaded = true).single()

            transaction {
                ChapterUserTable.batchInsert(listOf(1, userId2)) { userId ->
                    this[ChapterUserTable.chapter] = chapterId
                    this[ChapterUserTable.user] = userId
                    this[ChapterUserTable.isDownloadRequested] = true
                    this[ChapterUserTable.isDownloaded] = userId == 1
                }
            }

            Chapter.deleteDownloadedChapters(1, listOf(chapterId))

            val states =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.chapter eq chapterId) and (ChapterUserTable.user inList listOf(1, userId2)) }
                        .associate { it[ChapterUserTable.user].value to it }
                }
            // caller's state is cleared
            assertEquals(false, states.getValue(1)[ChapterUserTable.isDownloadRequested])
            assertEquals(false, states.getValue(1)[ChapterUserTable.isDownloaded])
            // the other user's state is untouched
            assertTrue(states.getValue(userId2)[ChapterUserTable.isDownloadRequested])
            assertEquals(false, states.getValue(userId2)[ChapterUserTable.isDownloaded])
            // the shared download is kept because it is still requested
            val downloaded =
                transaction {
                    ChapterTable
                        .select(ChapterTable.isDownloaded)
                        .where { ChapterTable.id eq chapterId }
                        .single()[ChapterTable.isDownloaded]
                }
            assertTrue(downloaded)
        }

    @Test
    fun deleteChapterClearsUserDownloadState() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_DELETE_CHAPTER_TEST")
            val chapterId =
                createChaptersForDownloadTest(mangaId, listOf("1"), downloaded = true).single()

            transaction {
                ChapterUserTable.batchInsert(listOf(1)) {
                    this[ChapterUserTable.chapter] = chapterId
                    this[ChapterUserTable.user] = 1
                    this[ChapterUserTable.isDownloadRequested] = true
                    this[ChapterUserTable.isDownloaded] = true
                }
            }

            Chapter.deleteChapter(1, mangaId, 1)

            val state =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.user eq 1) and (ChapterUserTable.chapter eq chapterId) }
                        .single()
                }
            assertEquals(false, state[ChapterUserTable.isDownloadRequested])
            assertEquals(false, state[ChapterUserTable.isDownloaded])

            val downloaded =
                transaction {
                    ChapterTable
                        .select(ChapterTable.isDownloaded)
                        .where { ChapterTable.id eq chapterId }
                        .single()[ChapterTable.isDownloaded]
                }
            assertEquals(false, downloaded)
        }

    @Test
    fun chapterUrlChangeMigratesPerUserDownloadStateWhenDownloadPreserved() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_MIGRATE_TEST")
            val chapter2Id =
                transaction {
                    ChapterTable
                        .batchInsert(listOf("1", "2", "3")) { url ->
                            this[ChapterTable.url] = url
                            this[ChapterTable.name] = url
                            this[ChapterTable.chapter_number] = url.toFloat()
                            this[ChapterTable.sourceOrder] = url.toInt()
                            this[ChapterTable.manga] = mangaId
                            this[ChapterTable.isDownloaded] = url == "2"
                            this[ChapterTable.pageCount] = if (url == "2") 10 else -1
                            this[ChapterTable.memo] = JsonObject.EMPTY
                        }.first { it[ChapterTable.url] == "2" }[ChapterTable.id]
                        .value
                }

            transaction {
                ChapterUserTable.batchInsert(listOf(1)) {
                    this[ChapterUserTable.chapter] = chapter2Id
                    this[ChapterUserTable.user] = 1
                    this[ChapterUserTable.isRead] = true
                    this[ChapterUserTable.isDownloadRequested] = true
                    this[ChapterUserTable.isDownloaded] = true
                }
            }

            val mangaEntry =
                transaction {
                    MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
                }

            // only the url of chapter 2 changed, name and scanlator are the same so the download is preserved
            val fetchedChapters =
                listOf("1", "2-new", "3").map { url ->
                    SChapter.create().apply {
                        this.url = url
                        this.name = url.removeSuffix("-new")
                        this.chapter_number = url.removeSuffix("-new").toFloat()
                    }
                }

            Chapter.updateChapterListDatabase(mangaEntry, fetchedChapters, source)

            val newChapter2Id =
                transaction {
                    ChapterTable
                        .select(ChapterTable.id)
                        .where { (ChapterTable.manga eq mangaId) and (ChapterTable.url eq "2-new") }
                        .single()[ChapterTable.id]
                        .value
                }

            val state =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.chapter eq newChapter2Id) and (ChapterUserTable.user eq 1) }
                        .single()
                }
            assertTrue(state[ChapterUserTable.isRead])
            assertTrue(state[ChapterUserTable.isDownloadRequested])
            assertTrue(state[ChapterUserTable.isDownloaded])

            val chapterRow =
                transaction {
                    ChapterTable.selectAll().where { ChapterTable.id eq newChapter2Id }.single()
                }
            assertTrue(chapterRow[ChapterTable.isDownloaded])
            assertEquals(10, chapterRow[ChapterTable.pageCount])
        }

    @Test
    fun chapterUrlChangeClearsPerUserDownloadStateWhenScanlatorChanged() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_MIGRATE_SCANLATOR_TEST")
            val chapter2Id =
                transaction {
                    ChapterTable
                        .batchInsert(listOf("1", "2", "3")) { url ->
                            this[ChapterTable.url] = url
                            this[ChapterTable.name] = url
                            this[ChapterTable.chapter_number] = url.toFloat()
                            this[ChapterTable.sourceOrder] = url.toInt()
                            this[ChapterTable.scanlator] = if (url == "2") "old" else null
                            this[ChapterTable.manga] = mangaId
                            this[ChapterTable.isDownloaded] = url == "2"
                            this[ChapterTable.pageCount] = if (url == "2") 10 else -1
                            this[ChapterTable.memo] = JsonObject.EMPTY
                        }.first { it[ChapterTable.url] == "2" }[ChapterTable.id]
                        .value
                }

            transaction {
                ChapterUserTable.batchInsert(listOf(1)) {
                    this[ChapterUserTable.chapter] = chapter2Id
                    this[ChapterUserTable.user] = 1
                    this[ChapterUserTable.isRead] = true
                    this[ChapterUserTable.isDownloadRequested] = true
                    this[ChapterUserTable.isDownloaded] = true
                }
            }

            val mangaEntry =
                transaction {
                    MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
                }

            // the url of chapter 2 changed and so did its scanlator, so the download cannot be preserved
            val fetchedChapters =
                listOf("1", "2-new", "3").map { url ->
                    SChapter.create().apply {
                        this.url = url
                        this.name = url.removeSuffix("-new")
                        this.chapter_number = url.removeSuffix("-new").toFloat()
                        if (url == "2-new") this.scanlator = "new"
                    }
                }

            Chapter.updateChapterListDatabase(mangaEntry, fetchedChapters, source)

            val newChapter2Id =
                transaction {
                    ChapterTable
                        .select(ChapterTable.id)
                        .where { (ChapterTable.manga eq mangaId) and (ChapterTable.url eq "2-new") }
                        .single()[ChapterTable.id]
                        .value
                }

            val state =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.chapter eq newChapter2Id) and (ChapterUserTable.user eq 1) }
                        .single()
                }
            // regular state is still migrated
            assertTrue(state[ChapterUserTable.isRead])
            // but the download state is gone for all users
            assertEquals(false, state[ChapterUserTable.isDownloadRequested])
            assertEquals(false, state[ChapterUserTable.isDownloaded])

            val chapterRow =
                transaction {
                    ChapterTable.selectAll().where { ChapterTable.id eq newChapter2Id }.single()
                }
            assertEquals(false, chapterRow[ChapterTable.isDownloaded])
        }

    @Test
    fun chapterNameChangeInvalidatingDownloadClearsPerUserState() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_INVALIDATE_TEST")
            val chapterId =
                transaction {
                    ChapterTable
                        .batchInsert(listOf("1")) {
                            this[ChapterTable.url] = "1"
                            this[ChapterTable.name] = "Chapter 1"
                            this[ChapterTable.chapter_number] = 1f
                            this[ChapterTable.sourceOrder] = 1
                            this[ChapterTable.manga] = mangaId
                            this[ChapterTable.isDownloaded] = true
                            this[ChapterTable.pageCount] = 10
                            this[ChapterTable.memo] = JsonObject.EMPTY
                        }.first()[ChapterTable.id]
                        .value
                }

            transaction {
                ChapterUserTable.batchInsert(listOf(1)) {
                    this[ChapterUserTable.chapter] = chapterId
                    this[ChapterUserTable.user] = 1
                    this[ChapterUserTable.isDownloadRequested] = true
                    this[ChapterUserTable.isDownloaded] = true
                }
            }

            // place a shared download file so the rename to the new chapter name fails,
            // which invalidates the download for all users
            val oldCbzFile = File(getChapterCbzPath(mangaId, "Chapter 1", null))
            val newCbzFile = File(getChapterCbzPath(mangaId, "Chapter 2", null))
            try {
                oldCbzFile.parentFile.mkdirs()
                oldCbzFile.writeText("fake download")
                newCbzFile.writeText("blocking destination")

                val mangaEntry =
                    transaction {
                        MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
                    }

                val fetchedChapters =
                    listOf(
                        SChapter.create().apply {
                            url = "1"
                            name = "Chapter 2"
                            chapter_number = 1f
                        },
                    )

                Chapter.updateChapterListDatabase(mangaEntry, fetchedChapters, source)

                val chapterRow =
                    transaction {
                        ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.single()
                    }
                assertEquals(false, chapterRow[ChapterTable.isDownloaded])
                assertEquals(-1, chapterRow[ChapterTable.pageCount])

                val state =
                    transaction {
                        ChapterUserTable
                            .selectAll()
                            .where { (ChapterUserTable.chapter eq chapterId) and (ChapterUserTable.user eq 1) }
                            .single()
                    }
                assertEquals(false, state[ChapterUserTable.isDownloadRequested])
                assertEquals(false, state[ChapterUserTable.isDownloaded])
            } finally {
                oldCbzFile.delete()
                newCbzFile.delete()
            }
        }

    private fun createChaptersForDownloadTest(
        mangaId: Int,
        urls: List<String>,
        downloaded: Boolean,
    ): List<Int> =
        transaction {
            ChapterTable
                .batchInsert(urls) { url ->
                    this[ChapterTable.url] = url
                    this[ChapterTable.name] = url
                    this[ChapterTable.chapter_number] = url.toFloat()
                    this[ChapterTable.sourceOrder] = url.toInt()
                    this[ChapterTable.manga] = mangaId
                    this[ChapterTable.isDownloaded] = downloaded
                    this[ChapterTable.pageCount] = if (downloaded) 10 else -1
                    this[ChapterTable.memo] = JsonObject.EMPTY
                }.map { it[ChapterTable.id].value }
        }

    private fun createSecondUser(): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[UserAccountTable.username] = "user2"
                    it[UserAccountTable.password] = "password"
                }.value
        }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
        transaction {
            UserAccountTable.deleteWhere { username eq "user2" }
        }
    }
}
