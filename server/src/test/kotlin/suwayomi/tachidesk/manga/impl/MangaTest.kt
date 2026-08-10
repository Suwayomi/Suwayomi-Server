package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MangaTest : ApplicationTest() {
    @Test
    fun `updateMangaDatabase passes memo to source when resolving real URL`() =
        runTest {
            val mangaId = createLibraryManga("MEMO_TEST")
            val mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.single() }
            val memo = buildJsonObject { put("slug", JsonPrimitive("memo-slug")) }
            val expectedUrl = "https://example.com/manga/memo-slug"
            val remoteManga =
                SManga.create().apply {
                    url = "MEMO_TEST"
                    title = "MEMO_TEST"
                    this.memo = memo
                }
            val source = mockk<HttpSource>()
            every { source.getMangaUrl(any()) } answers {
                check(firstArg<SManga>().memo == memo) { "Manga memo was not passed to the source" }
                expectedUrl
            }

            Manga.updateMangaDatabase(mangaEntry, source, remoteManga)

            val realUrl =
                transaction {
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaId }
                        .single()[MangaTable.realUrl]
                }
            assertEquals(expectedUrl, realUrl)
        }

    @Test
    fun getMangaMeta() {
        val metaManga = createLibraryManga("META_TEST")
        val emptyMeta = Manga.getMangaMetaMap(metaManga).size
        assertEquals(0, emptyMeta, "Default Manga meta should be empty at start")

        Manga.modifyMangaMeta(metaManga, "test", "value")
        assertEquals(1, Manga.getMangaMetaMap(metaManga).size, "Manga meta should have one member")
        assertEquals("value", Manga.getMangaMetaMap(metaManga)["test"], "Manga meta use the value 'value' for key 'test'")

        Manga.modifyMangaMeta(metaManga, "test", "newValue")
        assertEquals(
            1,
            Manga.getMangaMetaMap(metaManga).size,
            "Manga meta should still only have one pair",
        )
        assertEquals(
            "newValue",
            Manga.getMangaMetaMap(metaManga)["test"],
            "Manga meta with key 'test' should use the value `newValue`",
        )

        Manga.modifyMangaMeta(metaManga, "test2", "value2")
        assertEquals(
            2,
            Manga.getMangaMetaMap(metaManga).size,
            "Manga Meta should have an additional pair",
        )
        assertEquals(
            "value2",
            Manga.getMangaMetaMap(metaManga)["test2"],
            "Manga Meta for key 'test2' should be 'value2'",
        )
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            MangaMetaTable,
            MangaTable,
        )
    }
}
