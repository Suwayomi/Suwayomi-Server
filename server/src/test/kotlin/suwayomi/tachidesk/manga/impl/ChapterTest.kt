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
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
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
            assertTrue(user1State[ChapterUserTable.version] > 0L)

            val user2State = userStates.getValue(userId2)
            assertEquals(false, user2State[ChapterUserTable.isRead])
            assertEquals(false, user2State[ChapterUserTable.isBookmarked])
            assertEquals(2, user2State[ChapterUserTable.lastPageRead])
            assertEquals(500L, user2State[ChapterUserTable.lastReadAt])
            assertTrue(user2State[ChapterUserTable.version] > 0L)
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
