package suwayomi.tachidesk.opds

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import io.javalin.http.servlet.urlDecode
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.impl.OpdsFeedBuilder
import suwayomi.tachidesk.opds.repository.NavigationRepository
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpdsFeedLinksTest : ApplicationTest() {
    private fun createLibraryManga(titles: List<String>) {
        transaction {
            val mangaIds =
                MangaTable
                    .batchInsert(titles) {
                        this[MangaTable.title] = it
                        this[MangaTable.url] = it
                        this[MangaTable.sourceReference] = LocalSource.ID
                        this[MangaTable.genre] = "Slice of Life"
                        this[MangaTable.memo] = JsonObject.EMPTY
                    }.map { it[MangaTable.id].value }
            MangaUserTable.batchInsert(mangaIds) {
                this[MangaUserTable.manga] = it
                this[MangaUserTable.user] = 1
                this[MangaUserTable.inLibrary] = true
            }
        }
    }

    @Test
    fun searchPagesKeepTheQuery() {
        // One more than the default page size
        createLibraryManga((1..101).map { "Manga %03d".format(it) })

        val feed = OpdsFeedBuilder.getSearchFeed(1, "/api/opds/v1.2", Locale.ENGLISH, OpdsSearchCriteria("manga"), 2)

        assertEquals(1, feed.split("<entry").size - 1)
        assertTrue("<title>Manga 101</title>" in feed)
        assertTrue("/library/series?query=manga&amp;pageNumber=1&amp;lang=en" in feed)
        assertTrue("role=\"request\" searchTerms=\"manga\"" in feed)
        assertTrue(feed.indexOf("<opensearch:totalResults") < feed.indexOf("<entry"))
    }

    @Test
    fun genreIdsSurviveThePathDecoding() {
        createLibraryManga(listOf("Manga"))

        val genre = NavigationRepository.getGenres(1, Locale.ENGLISH).first.single()

        assertEquals("Slice of Life", urlDecode(genre.id))
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(MangaTable)
    }
}
