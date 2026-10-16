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
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
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
import suwayomi.tachidesk.manga.impl.chapter.refreshChapterPageList
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.dataclass.PAGINATION_FACTOR
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChapterTest : ApplicationTest() {
    private val source = StubSource(1L)

    // ids of users created by the running test — tearDown deletes by identity, never by a
    // broad "id != 1" predicate that would also remove other tests' fixtures
    private val createdUserIds = mutableListOf<Int>()

    @Test
    fun pageListRefreshPreservesEachUsersDownloadRequest() =
        runTest {
            val mangaId = createLibraryManga("PAGE_REFRESH_REQUESTS")
            val chapterId = createChaptersForDownloadTest(mangaId, listOf("1"), downloaded = true).single()
            val secondUser = createSecondUser()
            val thirdUser = createSecondUser("page-refresh-reader")
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
            val userId2 = createSecondUser()

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
    fun getRecentChaptersPaginatesWithHasNextPageSentinel() =
        runTest {
            val mangaId = createLibraryManga("recent-chapters-pagination")
            // inLibraryAt defaults to 0, so any fetchedAt > 0 qualifies as "recent"
            val chapterCount = PAGINATION_FACTOR + 5
            transaction {
                ChapterTable.batchInsert((1..chapterCount).toList()) { i ->
                    this[ChapterTable.url] = "recent-$i"
                    this[ChapterTable.name] = "recent-$i"
                    this[ChapterTable.sourceOrder] = i
                    this[ChapterTable.fetchedAt] = i.toLong()
                    this[ChapterTable.manga] = mangaId
                    this[ChapterTable.memo] = JsonObject.EMPTY
                }
            }

            val page0 = Chapter.getRecentChapters(1, 0)
            assertEquals(PAGINATION_FACTOR, page0.page.size)
            assertTrue(page0.hasNextPage)
            // ordered by fetched_at DESC — newest first
            val newest =
                page0.page
                    .first()
                    .chapter.name
            val oldestOnPage =
                page0.page
                    .last()
                    .chapter.name
            assertEquals("recent-$chapterCount", newest)
            assertEquals("recent-${chapterCount - PAGINATION_FACTOR + 1}", oldestOnPage)

            val page1 = Chapter.getRecentChapters(1, 1)
            assertEquals(5, page1.page.size)
            assertEquals(false, page1.hasNextPage)

            // a page past the end is empty instead of throwing
            val emptyPage = Chapter.getRecentChapters(1, 3)
            assertTrue(emptyPage.page.isEmpty())
            assertEquals(false, emptyPage.hasNextPage)

            // negative page numbers clamp to the first page (paginatedFrom semantics)
            val clamped = Chapter.getRecentChapters(1, -1)
            assertEquals(page0.page, clamped.page)
            assertEquals(page0.hasNextPage, clamped.hasNextPage)
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
    fun deleteChapterKeepsCommittedDbStateWhenFilesystemDeleteFails() =
        runTest {
            val mangaId = createLibraryManga("DOWNLOAD_DELETE_FS_FAIL_TEST")
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

            // a filesystem failure during the post-commit cleanup must propagate but
            // leave the committed DB state intact — leftover files are reclaimed on
            // the next refresh. Wrapping the delete in a transaction would instead
            // roll the flags back to "downloaded" while the files are half-gone.
            mockkObject(ChapterDownloadHelper)
            try {
                coEvery {
                    ChapterDownloadHelper.delete(mangaId, chapterId)
                } throws IOException("simulated filesystem failure")

                try {
                    Chapter.deleteChapter(1, mangaId, 1)
                    fail("deleteChapter should propagate the filesystem failure")
                } catch (expected: IOException) {
                    // expected — thrown post-commit
                }
            } finally {
                unmockkObject(ChapterDownloadHelper)
            }

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
    fun chapterListReadsBackPerUserReadState() =
        runTest {
            val mangaId = createLibraryManga("CHAPTER_LIST_READBACK")
            createChaptersForDownloadTest(mangaId, listOf("1", "2", "3"), downloaded = false)

            // the REST PATCH chapter endpoint (read=true&lastPageRead=5) funnels into modifyChapter
            Chapter.modifyChapter(
                userId = 1,
                mangaId = mangaId,
                chapterIndex = 3,
                isRead = true,
                isBookmarked = null,
                markPrevRead = null,
                lastPageRead = 5,
            )

            val chapters = Chapter.getChapterList(1, mangaId)
            val modified = chapters.first { it.index == 3 }
            assertTrue(modified.read)
            assertEquals(5, modified.lastPageRead)

            val untouched = chapters.first { it.index == 1 }
            assertEquals(false, untouched.read)
            assertEquals(0, untouched.lastPageRead)

            // the joined state is the caller's own — another user's list is unaffected
            val otherUser = createSecondUser("chapter-list-other")
            val otherChapters = Chapter.getChapterList(otherUser, mangaId)
            assertEquals(false, otherChapters.first { it.index == 3 }.read)
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

    private fun createSecondUser(name: String = "user2"): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[UserAccountTable.username] = name
                    it[UserAccountTable.password] = "password"
                }.value
        }.also { createdUserIds += it }

    @AfterEach
    internal fun tearDown() {
        // drain the shared DownloadManager first: queued entries reference the rows we are
        // about to delete, and a stale queue would bleed into the next test
        runBlocking { DownloadManager.clear() }
        clearTables(
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
        transaction {
            if (createdUserIds.isNotEmpty()) {
                UserAccountTable.deleteWhere { UserAccountTable.id inList createdUserIds }
                createdUserIds.clear()
            }
        }
        GetSource.unregisterSource(source.id)
    }
}
