package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.opds.repository.ChapterRepository
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A chapter only gets a [ChapterUserTable] row once the user acts on it, so freshly fetched
 * chapters read as NULL through the left join, and have to count as unread.
 */
class UnreadWithoutUserRowTest : GraphQLTest() {
    /** A manga with 3 chapters as a source fetch stores them, without any user row, and a 4th one read. */
    private fun createMangaWithFetchedChapters(): Pair<Int, List<Int>> {
        val mangaId = createLibraryManga("Manga")
        val chapterIds =
            transaction {
                ChapterTable
                    .batchInsert(1..4) {
                        this[ChapterTable.url] = "$mangaId-$it"
                        this[ChapterTable.name] = "$it"
                        this[ChapterTable.sourceOrder] = it
                        this[ChapterTable.manga] = mangaId
                        this[ChapterTable.memo] = JsonObject.EMPTY
                    }.map { it[ChapterTable.id].value }
                    .also { ids ->
                        ChapterUserTable.insert {
                            it[chapter] = ids.last()
                            it[user] = 1
                            it[isRead] = true
                        }
                    }
            }
        return mangaId to chapterIds
    }

    @Test
    fun graphqlUnreadCountIncludesChaptersWithoutUserRow() {
        val (mangaId, _) = createMangaWithFetchedChapters()

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    manga(id: ${'$'}id) {
                        unreadCount
                    }
                }
                """.trimIndent(),
                mapOf("id" to mangaId),
            )

        response.assertNoErrors()
        assertEquals(3, response.dataPath("manga", "unreadCount"))
    }

    @Test
    fun unreadChaptersIncludeChaptersWithoutUserRow() {
        val (mangaId, chapterIds) = createMangaWithFetchedChapters()

        assertEquals(chapterIds.dropLast(1).toSet(), Manga.getUnreadChapters(1, mangaId).map { it.id }.toSet())
    }

    @Test
    fun opdsUnreadCountIncludesChaptersWithoutUserRow() {
        val (mangaId, _) = createMangaWithFetchedChapters()

        val counts = ChapterRepository.getChapterFilterCounts(1, mangaId)

        assertEquals(3L, counts["unread"])
        assertEquals(1L, counts["read"])
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(ChapterTable, MangaTable)
    }
}
