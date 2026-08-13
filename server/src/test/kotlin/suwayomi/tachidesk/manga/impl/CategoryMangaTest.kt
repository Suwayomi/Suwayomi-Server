package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.impl.Category.DEFAULT_CATEGORY_ID
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga

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
            0,
            CategoryManga.getCategoryMangaList(DEFAULT_CATEGORY_ID)[0].unreadCount,
            "Manga should not have any unread chapters",
        )
        createChapters(mangaId, 10, false, start = 11)
        assertEquals(
            10,
            CategoryManga.getCategoryMangaList(DEFAULT_CATEGORY_ID)[0].unreadCount,
            "Manga should have unread chapters",
        )

        val categoryId = Category.createCategory("Old")
        assertEquals(
            0,
            CategoryManga.getCategoryMangaList(categoryId).size,
            "Newly created category shouldn't have any Mangas",
        )
        CategoryManga.addMangaToCategory(mangaId, categoryId)
        assertEquals(
            1,
            CategoryManga.getCategoryMangaList(categoryId).size,
            "Manga should been moved",
        )
        assertEquals(
            10,
            CategoryManga.getCategoryMangaList(categoryId)[0].unreadCount,
            "Manga should keep it's unread count in moved category",
        )
        assertEquals(
            0,
            CategoryManga.getCategoryMangaList(DEFAULT_CATEGORY_ID).size,
            "Manga shouldn't be member of default category after moving",
        )
    }

    @Test
    fun `duplicate manga-category pairing is rejected by the unique constraint`() {
        val mangaId = createLibraryManga("Naruto")
        val categoryId = Category.createCategory("Shonen")
        CategoryManga.addMangaToCategory(mangaId, categoryId)

        // Bypass the application layer's own duplicate checks and attempt to insert the same
        // (manga, category) pairing directly. This must be rejected by the DB-level unique
        // constraint added in M0061_PreventDuplicatedCategoryManga. If that constraint is ever
        // dropped or weakened, this insert would succeed and silently inflate category/library
        // counts.
        assertThrows(ExposedSQLException::class.java) {
            transaction {
                CategoryMangaTable.insert {
                    it[CategoryMangaTable.manga] = mangaId
                    it[CategoryMangaTable.category] = categoryId
                }
            }
        }

        val rowCount =
            transaction {
                CategoryMangaTable
                    .selectAll()
                    .where { (CategoryMangaTable.manga eq mangaId) and (CategoryMangaTable.category eq categoryId) }
                    .count()
            }
        assertEquals(1, rowCount, "Only one CategoryMangaTable row should exist for a given manga/category pairing")
    }

    @Test
    fun `adding manga to the same category twice does not create duplicate rows`() {
        val mangaId = createLibraryManga("One Piece")
        val categoryId = Category.createCategory("Adventure")

        // addMangasToCategories catches and swallows the unique constraint violation from a
        // duplicate (manga, category) pairing, so repeated calls should be no-ops rather than
        // throwing or creating duplicate CategoryMangaTable rows.
        CategoryManga.addMangasToCategories(listOf(mangaId), listOf(categoryId))
        CategoryManga.addMangasToCategories(listOf(mangaId), listOf(categoryId))

        val rowCount =
            transaction {
                CategoryMangaTable
                    .selectAll()
                    .where { (CategoryMangaTable.manga eq mangaId) and (CategoryMangaTable.category eq categoryId) }
                    .count()
            }
        assertEquals(1, rowCount, "Only one CategoryMangaTable row should exist for a given manga/category pairing")
        assertEquals(
            1,
            CategoryManga.getCategoryMangaList(categoryId).size,
            "Category size should reflect a single manga even if it was added twice",
        )
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            ChapterTable,
            CategoryMangaTable,
            MangaTable,
            CategoryTable,
        )
    }
}
