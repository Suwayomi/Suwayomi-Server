package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.BASE_PATH
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterTable.isRead
import suwayomi.tachidesk.manga.model.table.ChapterTable.manga
import suwayomi.tachidesk.manga.model.table.ChapterTable.name
import suwayomi.tachidesk.manga.model.table.ChapterTable.sourceOrder
import suwayomi.tachidesk.manga.model.table.ChapterTable.url
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.applicationSetup
import xyz.nulldev.ts.config.CONFIG_PREFIX
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryMangaTest {

    @BeforeEach
    fun setUp() {
        val dataRoot = File(BASE_PATH).absolutePath
        System.setProperty("$CONFIG_PREFIX.server.rootDir", dataRoot)
        applicationSetup()
    }

    @Test
    fun getCategoryMangaList() {
        val emptyCats = CategoryManga.getCategoryMangaList(0).size
        assertEquals(0, emptyCats, "Default category should be empty at start")
        val mangaId = createManga("Psyren")
        createChapters(mangaId, 10, true)
        assertEquals(1, CategoryManga.getCategoryMangaList(0).size, "Default category should have one member")
        assertEquals(
            0, CategoryManga.getCategoryMangaList(0)[0].unread_count,
            "Manga should not have any unread chapters"
        )
        createChapters(mangaId, 10, false)
        assertEquals(
            10, CategoryManga.getCategoryMangaList(0)[0].unread_count,
            "Manga should have unread chapters"
        )

        Category.createCategory("Old") // category id 1
        assertEquals(
            0,
            CategoryManga.getCategoryMangaList(1).size,
            "Newly created category shouldn't have any Mangas"
        )
        CategoryManga.addMangaToCategory(mangaId, 1)
        assertEquals(
            1, CategoryManga.getCategoryMangaList(1).size,
            "Manga should been moved"
        )
        assertEquals(
            10, CategoryManga.getCategoryMangaList(1)[0].unread_count,
            "Manga should keep it's unread count in moved category"
        )
        assertEquals(
            0, CategoryManga.getCategoryMangaList(0).size,
            "Manga shouldn't be member of default category after moving"
        )
    }

    private fun createManga(
        _title: String
    ): Int {
        return transaction {
            MangaTable.insertAndGetId {
                it[title] = _title
                it[url] = _title
                it[sourceReference] = 1
                it[defaultCategory] = true
                it[inLibrary] = true
            }.value
        }
    }

    private fun createChapters(
        mangaId: Int,
        amount: Int,
        read: Boolean
    ) {
        val list = listOf((0 until amount)).flatten().map { 1 }
        transaction {
            ChapterTable
                .batchInsert(list) {
                    this[url] = "$it"
                    this[name] = "$it"
                    this[sourceOrder] = it
                    this[isRead] = read
                    this[manga] = mangaId
                }
        }
    }

    @AfterEach
    internal fun tearDown() {
        transaction {
            ChapterTable.deleteAll()
            CategoryMangaTable.deleteAll()
            MangaTable.deleteAll()
            CategoryTable.deleteAll()
        }
    }
}
