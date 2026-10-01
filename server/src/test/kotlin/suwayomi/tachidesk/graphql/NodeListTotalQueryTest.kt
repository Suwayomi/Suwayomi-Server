package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.slf4j.LoggerFactory
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A list field counts its nodes in SQL when `totalCount` is all the caller selected, and loads them otherwise. */
class NodeListTotalQueryTest : GraphQLTest() {
    /** The chapters selection's result, and the SQL statements it took. */
    private fun queryChapters(selection: String): Pair<Map<*, *>, List<String>> {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 3, read = false)

        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val exposedLogger = LoggerFactory.getLogger("Exposed") as Logger
        exposedLogger.addAppender(appender)
        val response =
            try {
                graphql(
                    """
                    query(${'$'}id: Int!) {
                        manga(id: ${'$'}id) {
                            chapters {
                                $selection
                            }
                        }
                    }
                    """.trimIndent(),
                    mapOf("id" to mangaId),
                )
            } finally {
                exposedLogger.detachAppender(appender)
            }

        response.assertNoErrors()
        return response.dataPath("manga", "chapters") as Map<*, *> to appender.list.map { it.formattedMessage }
    }

    private fun List<String>.loadsChapterRows() = any { "CHAPTER.URL" in it }

    private fun List<String>.countsChapters() = any { "COUNT(CHAPTER.ID)" in it }

    @Test
    fun onlyTotalCountIsCounted() {
        val (chapters, statements) = queryChapters("totalCount")

        assertEquals(3, chapters["totalCount"])
        assertTrue(statements.countsChapters(), "expected a COUNT, ran $statements")
        assertFalse(statements.loadsChapterRows(), "expected no chapter rows to be loaded, ran $statements")
    }

    @Test
    fun typenameAlongTheTotalIsStillCounted() {
        val (chapters, statements) = queryChapters("__typename totalCount")

        assertEquals(3, chapters["totalCount"])
        assertEquals("ChapterNodeList", chapters["__typename"])
        assertFalse(statements.loadsChapterRows(), "expected no chapter rows to be loaded, ran $statements")
    }

    @Test
    fun selectedNodesAreLoaded() {
        val (chapters, statements) = queryChapters("totalCount nodes { id }")

        assertEquals(3, chapters["totalCount"])
        assertEquals(3, (chapters["nodes"] as List<*>).size)
        assertTrue(statements.loadsChapterRows(), "expected the chapter rows to be loaded, ran $statements")
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(ChapterTable, MangaTable)
    }
}
