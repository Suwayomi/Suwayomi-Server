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
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga

class ChapterDataLoaderTest : ApplicationTest() {
    private val createdUserIds = mutableListOf<Int>()

    @AfterEach
    fun cleanup() {
        clearTables(ChapterUserTable, ChapterTable, MangaTable)
        transaction {
            createdUserIds.forEach { userId ->
                UserAccountTable.deleteWhere { UserAccountTable.id eq userId }
            }
        }
        createdUserIds.clear()
    }

    private fun insertChapter(
        mangaId: Int,
        name: String,
        sourceOrder: Int,
        userId: Int? = 1,
        read: Boolean = false,
        downloaded: Boolean = false,
        bookmarked: Boolean = false,
        chapterNumber: Float = sourceOrder.toFloat(),
        lastReadAt: Long = 0L,
        fetchedAt: Long = 0L,
        dateUpload: Long = 0L,
    ): Int =
        transaction {
            val chapterId =
                ChapterTable
                    .insertAndGetId {
                        it[ChapterTable.url] = "ch-$mangaId-$sourceOrder"
                        it[ChapterTable.name] = name
                        it[ChapterTable.sourceOrder] = sourceOrder
                        it[ChapterTable.manga] = mangaId
                        it[ChapterTable.chapter_number] = chapterNumber
                        it[ChapterTable.fetchedAt] = fetchedAt
                        it[ChapterTable.date_upload] = dateUpload
                        it[ChapterTable.memo] = JsonObject.EMPTY
                    }.value

            userId?.let {
                insertChapterUser(
                    chapterId = chapterId,
                    userId = it,
                    read = read,
                    downloaded = downloaded,
                    bookmarked = bookmarked,
                    lastReadAt = lastReadAt,
                )
            }
            chapterId
        }

    private fun insertChapterUser(
        chapterId: Int,
        userId: Int,
        read: Boolean = false,
        downloaded: Boolean = false,
        bookmarked: Boolean = false,
        lastReadAt: Long = 0L,
    ) {
        transaction {
            ChapterUserTable.insert {
                it[chapter] = chapterId
                it[user] = userId
                it[isRead] = read
                it[isDownloaded] = downloaded
                it[isBookmarked] = bookmarked
                it[ChapterUserTable.lastReadAt] = lastReadAt
            }
        }
    }

    private fun createUser(username: String): Int =
        transaction {
            val userId =
                UserAccountTable
                    .insertAndGetId {
                        it[UserAccountTable.username] = username
                        it[password] = "password"
                    }.value
            createdUserIds.add(userId)
            userId
        }

    // -- firstChapterPerManga tests --

    @Test
    fun `returns empty map for empty manga ids`() {
        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = emptyList(),
                    userId = 1,
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
                    userId = 1,
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
                    userId = 1,
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
                    userId = 1,
                    filter = ChapterUserTable.isRead eq true,
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
                    userId = 1,
                    filter = ChapterUserTable.isRead eq true,
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
                    userId = 1,
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
                    userId = 1,
                    orderBy = listOf(ChapterUserTable.lastReadAt to SortOrder.DESC),
                )
            }

        assertEquals("Ch 2", result[manga1]?.name)
    }

    @Test
    fun `lastRead - breaks equal timestamps by highest sourceOrder`() {
        val mangaId = createLibraryManga("Last Read Tie")
        insertChapter(mangaId, "Ch 1", sourceOrder = 1, lastReadAt = 300L)
        insertChapter(mangaId, "Ch 2", sourceOrder = 2, lastReadAt = 300L)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = 1,
                    orderBy =
                        listOf(
                            ChapterUserTable.lastReadAt to SortOrder.DESC,
                            ChapterTable.sourceOrder to SortOrder.DESC,
                        ),
                )
            }

        assertEquals("Ch 2", result[mangaId]?.name)
    }

    @Test
    fun `lastRead - a real timestamp wins over a missing user row`() {
        val mangaId = createLibraryManga("Last Read Missing State")
        insertChapter(mangaId, "Never read", sourceOrder = 2, userId = null)
        insertChapter(mangaId, "Actually read", sourceOrder = 1, lastReadAt = 300L)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = 1,
                    orderBy =
                        listOf(
                            ChapterUserTable.lastReadAt to SortOrder.DESC_NULLS_LAST,
                            ChapterTable.sourceOrder to SortOrder.DESC,
                        ),
                )
            }

        assertEquals("Actually read", result[mangaId]?.name)
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
                    userId = 1,
                    filter = ChapterUserTable.isRead eq true,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.DESC),
                )
            }

        assertEquals("Ch 2", result[manga1]?.name)
    }

    @Test
    fun `latestRead - excludes a chapter with no user row`() {
        val mangaId = createLibraryManga("Latest Read Missing State")
        insertChapter(mangaId, "Read chapter", sourceOrder = 1, read = true)
        insertChapter(mangaId, "No user state", sourceOrder = 2, userId = null)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = 1,
                    filter = ChapterUserTable.isRead eq true,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.DESC),
                )
            }

        assertEquals("Read chapter", result[mangaId]?.name)
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
                    userId = 1,
                    filter = ChapterUserTable.isRead eq false,
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
                    userId = 1,
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
                    userId = 1,
                    orderBy = listOf(ChapterTable.date_upload to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC),
                )
            }

        assertEquals("Ch 1", result[manga1]?.name)
    }

    @Test
    fun `latestUploaded - breaks equal upload dates by highest sourceOrder`() {
        val mangaId = createLibraryManga("Upload Date Tie")
        insertChapter(mangaId, "Ch 1", sourceOrder = 1, dateUpload = 500L)
        insertChapter(mangaId, "Ch 2", sourceOrder = 2, dateUpload = 500L)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = 1,
                    orderBy = listOf(ChapterTable.date_upload to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC),
                )
            }

        assertEquals("Ch 2", result[mangaId]?.name)
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
                    userId = 1,
                    filter = ChapterTable.chapter_number greater 0f,
                    orderBy = listOf(ChapterTable.chapter_number to SortOrder.DESC_NULLS_LAST),
                )
            }

        assertEquals("Ch 2", result[manga1]?.name)
    }

    @Test
    fun `highestNumbered - breaks equal numbers by highest sourceOrder`() {
        val mangaId = createLibraryManga("Chapter Number Tie")
        insertChapter(mangaId, "Ch 1", sourceOrder = 1, chapterNumber = 50f)
        insertChapter(mangaId, "Ch 2", sourceOrder = 2, chapterNumber = 50f)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = 1,
                    filter = ChapterTable.chapter_number greater 0f,
                    orderBy =
                        listOf(
                            ChapterTable.chapter_number to SortOrder.DESC_NULLS_LAST,
                            ChapterTable.sourceOrder to SortOrder.DESC,
                        ),
                )
            }

        assertEquals("Ch 2", result[mangaId]?.name)
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
                    userId = 1,
                    filter = ChapterUserTable.isRead eq false,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertEquals("M1-Ch1", result[manga1]?.name)
        assertEquals("M2-Ch2", result[manga2]?.name)
        assertNull(result[manga3])
    }

    @Test
    fun `firstUnread treats a missing chapter user row as unread`() {
        val mangaId = createLibraryManga("Missing User Row")
        insertChapter(mangaId, "Read chapter", sourceOrder = 1, read = true)
        insertChapter(mangaId, "Implicitly unread chapter", sourceOrder = 2, userId = null)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = 1,
                    filter = ChapterUserTable.isRead eq false or ChapterUserTable.isRead.isNull(),
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertEquals("Implicitly unread chapter", result[mangaId]?.name)
        assertEquals(false, result[mangaId]?.isRead)
    }

    @Test
    fun `a row belonging only to another user counts as missing and unread`() {
        val secondUserId = createUser("chapter-loader-wrong-user")
        val mangaId = createLibraryManga("Wrong User State")
        insertChapter(mangaId, "Read chapter", sourceOrder = 1, read = true)
        val otherUserChapterId = insertChapter(mangaId, "Other user's chapter", sourceOrder = 2, userId = null)
        insertChapterUser(otherUserChapterId, secondUserId, read = true)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = 1,
                    filter = ChapterUserTable.isRead eq false or ChapterUserTable.isRead.isNull(),
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertEquals("Other user's chapter", result[mangaId]?.name)
        assertEquals(false, result[mangaId]?.isRead)
    }

    @Test
    fun `chapter selection is isolated by user`() {
        val secondUserId = createUser("chapter-loader-isolation")
        val mangaId = createLibraryManga("User Isolation")
        val firstChapterId = insertChapter(mangaId, "Chapter 1", sourceOrder = 1, read = false)
        val secondChapterId = insertChapter(mangaId, "Chapter 2", sourceOrder = 2, read = true)
        insertChapterUser(firstChapterId, secondUserId, read = true)
        insertChapterUser(secondChapterId, secondUserId, read = false)

        val userOneResult =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = 1,
                    filter = ChapterUserTable.isRead eq false or ChapterUserTable.isRead.isNull(),
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }
        val userTwoResult =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = secondUserId,
                    filter = ChapterUserTable.isRead eq false or ChapterUserTable.isRead.isNull(),
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertEquals("Chapter 1", userOneResult[mangaId]?.name)
        assertEquals("Chapter 2", userTwoResult[mangaId]?.name)
    }

    @Test
    fun `returned chapter contains state for the requested user`() {
        val secondUserId = createUser("chapter-loader-state")
        val mangaId = createLibraryManga("User State")
        val chapterId =
            insertChapter(
                mangaId = mangaId,
                name = "Chapter 1",
                sourceOrder = 1,
                read = false,
                downloaded = false,
                bookmarked = false,
                lastReadAt = 10L,
            )
        insertChapterUser(
            chapterId = chapterId,
            userId = secondUserId,
            read = true,
            downloaded = true,
            bookmarked = true,
            lastReadAt = 20L,
        )

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = secondUserId,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }.getValue(mangaId)

        assertEquals(true, result.isRead)
        assertEquals(true, result.isDownloaded)
        assertEquals(true, result.isBookmarked)
        assertEquals(20L, result.lastReadAt)
    }

    @Test
    fun `multiple users do not duplicate chapters in a manga result`() {
        val secondUserId = createUser("chapter-loader-duplicates")
        val mangaId = createLibraryManga("No Cross-user Duplicates")
        val chapterId = insertChapter(mangaId, "Chapter 1", sourceOrder = 1)
        insertChapterUser(chapterId, secondUserId, read = true)

        val result =
            transaction {
                firstChapterPerManga(
                    mangaIds = listOf(mangaId),
                    userId = 1,
                    orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                )
            }

        assertEquals(1, result.size)
        assertEquals("Chapter 1", result[mangaId]?.name)
    }
}
