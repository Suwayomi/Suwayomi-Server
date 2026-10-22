package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.chapter.refreshChapterPageList
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.settings.UserSettings
import suwayomi.tachidesk.server.settings.set
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChapterTest : ApplicationTest() {
    private val source = StubSource(1L)

    @Test
    fun pageListRefreshPreservesEachUsersDownloadRequest() =
        runTest {
            val mangaId = createLibraryManga("PAGE_REFRESH_REQUESTS")
            val chapterId = createChaptersForDownloadTest(mangaId, listOf("1"), downloaded = true).single()
            val secondUser = createSecondUser()
            val thirdUser =
                transaction {
                    UserAccountTable
                        .insertAndGetId {
                            it[username] = "page-refresh-reader"
                            it[password] = "password"
                        }.value
                }
            transaction {
                ChapterUserTable.batchInsert(listOf(1, secondUser, thirdUser)) { userId ->
                    this[ChapterUserTable.chapter] = chapterId
                    this[ChapterUserTable.user] = userId
                    this[ChapterUserTable.isDownloadRequested] = userId != thirdUser
                    this[ChapterUserTable.isDownloaded] = true
                }
            }
            val pageSource = mockk<Source>()
            coEvery { pageSource.getPageList(any()) } returns listOf(Page(0, "page", "image"))
            GetSource.registerSource(source.id to pageSource)

            assertEquals(1, refreshChapterPageList(mangaId, chapterId))
            transaction {
                val rows = ChapterUserTable.selectAll().where { ChapterUserTable.chapter eq chapterId }.toList()
                assertEquals(3, rows.size)
                rows.forEach { row ->
                    assertEquals(row[ChapterUserTable.user].value != thirdUser, row[ChapterUserTable.isDownloadRequested])
                    assertEquals(false, row[ChapterUserTable.isDownloaded])
                }
                val chapter = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.single()
                assertEquals(false, chapter[ChapterTable.isDownloaded])
                assertEquals(1, chapter[ChapterTable.pageCount])
            }
        }

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
    fun deleteDownloadedChaptersRemovesSharedDownloadWhenLastRequestingUserDeletes() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_LAST_USER_TEST")
            val userId2 = createSecondUser()
            val chapterId =
                createChaptersForDownloadTest(mangaId, listOf("1"), downloaded = true).single()

            // both users request the shared download
            DownloadManager.enqueue(1, listOf(chapterId))
            DownloadManager.enqueue(userId2, listOf(chapterId))

            // the first user deletes; the shared download is kept
            Chapter.deleteDownloadedChapters(1, listOf(chapterId))

            var downloaded =
                transaction {
                    ChapterTable
                        .select(ChapterTable.isDownloaded)
                        .where { ChapterTable.id eq chapterId }
                        .single()[ChapterTable.isDownloaded]
                }
            assertTrue(downloaded)

            // the last requesting user deletes; the shared download is removed
            Chapter.deleteDownloadedChapters(userId2, listOf(chapterId))

            downloaded =
                transaction {
                    ChapterTable
                        .select(ChapterTable.isDownloaded)
                        .where { ChapterTable.id eq chapterId }
                        .single()[ChapterTable.isDownloaded]
                }
            assertEquals(false, downloaded)

            val states =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.chapter eq chapterId) and (ChapterUserTable.user inList listOf(1, userId2)) }
                        .associate { it[ChapterUserTable.user].value to it }
                }
            assertEquals(false, states.getValue(1)[ChapterUserTable.isDownloadRequested])
            assertEquals(false, states.getValue(userId2)[ChapterUserTable.isDownloadRequested])
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

    @Test
    fun duplicateOfReadChapterGetsMarkedAsRead() =
        runTest {
            val mangaId = createLibraryManga("DUPLICATE_OF_READ")
            userConfig.markDuplicateReadChaptersAsRead.set(1, true)
            createDuplicateTestChapter(mangaId, "scanlator-a/1", readByUserId = mapOf(1 to true))

            fetchDuplicateTestChapters(mangaId, "scanlator-a/1" to 1f, "scanlator-b/1" to 1f)

            assertTrue(isRead("scanlator-b/1", 1), "The duplicate of an already read chapter should be read")
            assertTrue(isRead("scanlator-a/1", 1), "The already read chapter should still be read")
        }

    @Test
    fun duplicateOfReadChapterStaysUnreadWhileSettingIsDisabled() =
        runTest {
            val mangaId = createLibraryManga("DUPLICATE_SETTING_DISABLED")
            createDuplicateTestChapter(mangaId, "scanlator-a/1", readByUserId = mapOf(1 to true))

            fetchDuplicateTestChapters(mangaId, "scanlator-a/1" to 1f, "scanlator-b/1" to 1f)

            assertFalse(isRead("scanlator-b/1", 1), "The duplicate should not be touched while the setting is disabled")
        }

    @Test
    fun duplicateOfUnreadChapterStaysUnread() =
        runTest {
            val mangaId = createLibraryManga("DUPLICATE_OF_UNREAD")
            userConfig.markDuplicateReadChaptersAsRead.set(1, true)
            createDuplicateTestChapter(mangaId, "scanlator-a/1", readByUserId = mapOf(1 to false))

            fetchDuplicateTestChapters(mangaId, "scanlator-a/1" to 1f, "scanlator-b/1" to 1f)

            assertFalse(isRead("scanlator-b/1", 1), "The duplicate of an unread chapter should stay unread")
        }

    @Test
    fun chapterWithoutRecognizedNumberIsNoDuplicate() =
        runTest {
            val mangaId = createLibraryManga("DUPLICATE_UNRECOGNIZED")
            userConfig.markDuplicateReadChaptersAsRead.set(1, true)
            createDuplicateTestChapter(mangaId, "scanlator-a/oneshot", chapterNumber = -1f, readByUserId = mapOf(1 to true))

            fetchDuplicateTestChapters(mangaId, "scanlator-a/oneshot" to -1f, "scanlator-b/oneshot" to -1f)

            assertFalse(isRead("scanlator-b/oneshot", 1), "Chapters without a recognized number are never duplicates")
        }

    @Test
    fun duplicateThatIsAlreadyInTheDatabaseGetsMarkedAsRead() =
        runTest {
            val mangaId = createLibraryManga("DUPLICATE_EXISTING")
            userConfig.markDuplicateReadChaptersAsRead.set(1, true)
            createDuplicateTestChapter(mangaId, "scanlator-a/1", readByUserId = mapOf(1 to true))
            createDuplicateTestChapter(mangaId, "scanlator-b/1", readByUserId = mapOf(1 to false))
            // "no user row" is the same as "unread"
            createDuplicateTestChapter(mangaId, "scanlator-c/1")

            // nothing new gets fetched, all chapters are kept
            fetchDuplicateTestChapters(mangaId, "scanlator-a/1" to 1f, "scanlator-b/1" to 1f, "scanlator-c/1" to 1f)

            assertTrue(isRead("scanlator-b/1", 1), "A duplicate with an unread user row should be marked as read")
            assertTrue(isRead("scanlator-c/1", 1), "A duplicate without a user row should be marked as read")
        }

    @Test
    fun duplicateIsOnlyMarkedAsReadForUsersThatReadTheChapter() =
        runTest {
            val mangaId = createLibraryManga("DUPLICATE_PER_USER_READ")
            val secondUser = createSecondUser()
            userConfig.markDuplicateReadChaptersAsRead.set(1, true)
            userConfig.markDuplicateReadChaptersAsRead.set(secondUser, true)
            createDuplicateTestChapter(mangaId, "scanlator-a/1", readByUserId = mapOf(1 to true, secondUser to false))

            fetchDuplicateTestChapters(mangaId, "scanlator-a/1" to 1f, "scanlator-b/1" to 1f)

            assertTrue(isRead("scanlator-b/1", 1), "The user that read the chapter should get the duplicate marked")
            assertFalse(isRead("scanlator-b/1", secondUser), "The user that did not read it should not be affected")
        }

    @Test
    fun duplicateIsOnlyMarkedAsReadForUsersThatEnabledTheSetting() =
        runTest {
            val mangaId = createLibraryManga("DUPLICATE_PER_USER_SETTING")
            val secondUser = createSecondUser()
            userConfig.markDuplicateReadChaptersAsRead.set(1, true)
            createDuplicateTestChapter(mangaId, "scanlator-a/1", readByUserId = mapOf(1 to true, secondUser to true))

            fetchDuplicateTestChapters(mangaId, "scanlator-a/1" to 1f, "scanlator-b/1" to 1f)

            assertTrue(isRead("scanlator-b/1", 1), "The user that enabled the setting should get the duplicate marked")
            assertFalse(isRead("scanlator-b/1", secondUser), "The user that did not enable it should not be affected")
        }

    @Test
    fun readStateOfKeptChapterIsNeverCleared() =
        runTest {
            val mangaId = createLibraryManga("DUPLICATE_KEEPS_READ")
            userConfig.markDuplicateReadChaptersAsRead.set(1, true)
            createDuplicateTestChapter(mangaId, "scanlator-a/1", readByUserId = mapOf(1 to true))
            createDuplicateTestChapter(mangaId, "scanlator-a/2", chapterNumber = 2f, readByUserId = mapOf(1 to true))

            fetchDuplicateTestChapters(mangaId, "scanlator-a/1" to 1f, "scanlator-a/2" to 2f)

            assertTrue(isRead("scanlator-a/1", 1), "A read chapter should stay read")
            assertTrue(isRead("scanlator-a/2", 1), "A read chapter should stay read")
        }

    private fun createDuplicateTestChapter(
        mangaId: Int,
        url: String,
        chapterNumber: Float = 1f,
        readByUserId: Map<Int, Boolean> = emptyMap(),
    ) {
        transaction {
            val chapterId =
                ChapterTable
                    .insertAndGetId {
                        it[ChapterTable.url] = url
                        it[ChapterTable.name] = getDuplicateTestChapterName(chapterNumber)
                        it[ChapterTable.chapter_number] = chapterNumber
                        it[ChapterTable.sourceOrder] = 1
                        it[ChapterTable.manga] = mangaId
                        it[ChapterTable.memo] = JsonObject.EMPTY
                    }.value

            ChapterUserTable.batchInsert(readByUserId.entries) { (userId, isRead) ->
                this[ChapterUserTable.chapter] = chapterId
                this[ChapterUserTable.user] = userId
                this[ChapterUserTable.isRead] = isRead
            }
        }
    }

    private suspend fun fetchDuplicateTestChapters(
        mangaId: Int,
        vararg chapterNumberByUrl: Pair<String, Float>,
    ) {
        val mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }
        val fetchedChapters =
            chapterNumberByUrl.map { (url, chapterNumber) ->
                SChapter.create().apply {
                    this.url = url
                    this.name = getDuplicateTestChapterName(chapterNumber)
                    this.chapter_number = chapterNumber
                }
            }

        Chapter.updateChapterListDatabase(mangaEntry, fetchedChapters, source)
    }

    /** A chapter without a recognized number must not contain anything a chapter number could be parsed from */
    private fun getDuplicateTestChapterName(chapterNumber: Float): String =
        if (chapterNumber < 0f) "Oneshot" else "Chapter ${chapterNumber.toInt()}"

    /** A chapter without a user row is unread for that user */
    private fun isRead(
        url: String,
        userId: Int,
    ): Boolean =
        transaction {
            ChapterUserTable
                .innerJoin(ChapterTable, onColumn = { ChapterUserTable.chapter }, otherColumn = { ChapterTable.id })
                .select(ChapterUserTable.isRead)
                .where { (ChapterTable.url eq url) and (ChapterUserTable.user eq userId) }
                .singleOrNull()
                ?.get(ChapterUserTable.isRead) ?: false
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
        // the settings are cached, thus, deleting their rows together with the users is not enough
        transaction { UserAccountTable.select(UserAccountTable.id).map { it[UserAccountTable.id].value } }
            .forEach { userId -> UserSettings.reset(userId, userConfig.markDuplicateReadChaptersAsRead) }

        clearTables(
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
        transaction {
            UserAccountTable.deleteWhere { id neq 1 }
        }
        GetSource.unregisterSource(source.id)
    }
}
