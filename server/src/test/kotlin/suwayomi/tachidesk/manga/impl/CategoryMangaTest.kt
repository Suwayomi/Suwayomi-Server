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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.impl.Category.DEFAULT_CATEGORY_ID
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterTable.isRead
import suwayomi.tachidesk.manga.model.table.ChapterTable.manga
import suwayomi.tachidesk.manga.model.table.ChapterTable.name
import suwayomi.tachidesk.manga.model.table.ChapterTable.sourceOrder
import suwayomi.tachidesk.manga.model.table.ChapterTable.url
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.ApplicationTest

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryMangaTest : ApplicationTest() {
    @Test
    fun getCategoryMangaList() {
        val emptyCats = CategoryManga.getCategoryMangaList(DEFAULT_CATEGORY_ID).size
        assertEquals(0, emptyCats, "Default category should be empty at start")
        val mangaId = createLibraryManga("Psyren")
        createChapters(mangaId, 10, true)
        assertEquals(1, CategoryManga.getCategoryMangaList(DEFAULT_CATEGORY_ID).size, "Default category should have one member")
        assertEquals(
            0, CategoryManga.getCategoryMangaList(DEFAULT_CATEGORY_ID)[0].unreadCount,
            "Manga should not have any unread chapters"
        )
        createChapters(mangaId, 10, false)
        assertEquals(
            10, CategoryManga.getCategoryMangaList(DEFAULT_CATEGORY_ID)[0].unreadCount,
            "Manga should have unread chapters"
        )

        val categoryId = Category.createCategory("Old")
        assertEquals(
            0,
            CategoryManga.getCategoryMangaList(categoryId).size,
            "Newly created category shouldn't have any Mangas"
        )
        CategoryManga.addMangaToCategory(mangaId, categoryId)
        assertEquals(
            1, CategoryManga.getCategoryMangaList(categoryId).size,
            "Manga should been moved"
        )
        assertEquals(
            10, CategoryManga.getCategoryMangaList(categoryId)[0].unreadCount,
            "Manga should keep it's unread count in moved category"
        )
        assertEquals(
            0, CategoryManga.getCategoryMangaList(DEFAULT_CATEGORY_ID).size,
            "Manga shouldn't be member of default category after moving"
        )
    }

    private fun createLibraryManga(
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
