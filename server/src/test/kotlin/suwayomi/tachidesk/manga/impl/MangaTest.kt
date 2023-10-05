package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

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
