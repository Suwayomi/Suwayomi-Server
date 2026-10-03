package suwayomi.tachidesk.graphql

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
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A chapter only gets a [ChapterUserTable] row once the user acts on it, so
 * freshly fetched chapters have none and read as NULL through the left join.
 */
class MangaChapterFieldsQueryTest : GraphQLTest() {
    /** Chapters as a source fetch stores them: without any user row. */
    private fun createFetchedChapters(
        mangaId: Int,
        amount: Int,
    ): List<Int> =
        transaction {
            ChapterTable
                .batchInsert(1..amount) {
                    this[ChapterTable.url] = "$mangaId-$it"
                    this[ChapterTable.name] = "$it"
                    this[ChapterTable.sourceOrder] = it
                    this[ChapterTable.manga] = mangaId
                    this[ChapterTable.memo] = JsonObject.EMPTY
                }.map { it[ChapterTable.id].value }
        }

    private fun queryMangaChapter(
        mangaId: Int,
        field: String,
    ): Any? {
        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    manga(id: ${'$'}id) {
                        $field {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("id" to mangaId),
            )
        response.assertNoErrors()
        return response.dataPath("manga", field, "id")
    }

    @Test
    fun firstUnreadChapterIncludesChaptersWithoutUserRow() {
        val mangaId = createLibraryManga("Manga")
        val chapterIds = createFetchedChapters(mangaId, 3)

        assertEquals(chapterIds.first(), queryMangaChapter(mangaId, "firstUnreadChapter"))
    }

    @Test
    fun lastReadChapterIgnoresChaptersWithoutUserRow() {
        val mangaId = createLibraryManga("Manga")
        val chapterIds = createFetchedChapters(mangaId, 3)
        transaction {
            ChapterUserTable.insert {
                it[chapter] = chapterIds[1]
                it[user] = 1
                it[isRead] = true
                it[lastReadAt] = 100L
            }
        }

        assertEquals(chapterIds[1], queryMangaChapter(mangaId, "lastReadChapter"))
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(ChapterTable, MangaTable)
    }
}
