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
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import suwayomi.tachidesk.test.ensureDefaultCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryMangaTest : ApplicationTest() {
    @BeforeEach
    fun setUp() {
        // other test classes clear the CATEGORY table without restoring user 1's default row
        ensureDefaultCategory(1)
    }

    @Test
    fun getCategoryMangaList() {
        val defaultCategoryId = Category.getDefaultCategoryId(1)!!
        val emptyCats = CategoryManga.getCategoryMangaList(1, defaultCategoryId).size
        assertEquals(0, emptyCats, "Default category should be empty at start")
        val mangaId = createLibraryManga("Psyren")
        createChapters(mangaId, 10, true)
        assertEquals(1, CategoryManga.getCategoryMangaList(1, defaultCategoryId).size, "Default category should have one member")
        assertEquals(
            0,
            CategoryManga.getCategoryMangaList(1, defaultCategoryId)[0].unreadCount,
            "Manga should not have any unread chapters",
        )
        createChapters(mangaId, 10, false, start = 11)
        assertEquals(
            10,
            CategoryManga.getCategoryMangaList(1, defaultCategoryId)[0].unreadCount,
            "Manga should have unread chapters",
        )

        val categoryId = Category.createCategory(1, "Old")
        assertEquals(
            0,
            CategoryManga.getCategoryMangaList(1, categoryId).size,
            "Newly created category shouldn't have any Mangas",
        )
        CategoryManga.addMangaToCategory(1, mangaId, categoryId)
        assertEquals(
            1,
            CategoryManga.getCategoryMangaList(1, categoryId).size,
            "Manga should been moved",
        )
        assertEquals(
            10,
            CategoryManga.getCategoryMangaList(1, categoryId)[0].unreadCount,
            "Manga should keep it's unread count in moved category",
        )
        assertEquals(
            0,
            CategoryManga.getCategoryMangaList(1, defaultCategoryId).size,
            "Manga shouldn't be member of default category after moving",
        )
    }

    @Test
    fun `getCategoryMangaList query stays valid when user data columns are selected`() {
        // Regression test for the GROUP BY / SELECT column mismatch in getCategoryMangaList.
        //
        // The query selects the joined user-data columns (MangaUserTable.*) alongside the
        // MangaTable columns, so the GROUP BY must cover the full joined column set
        // (MangaTable.getWithUserData(userId).columns). Grouping by only MangaTable.columns
        // leaves the user-data columns ungrouped and non-aggregated, which Postgres rejects
        // with "column ... must appear in the GROUP BY clause". H2 is lenient about this, so
        // the failure only surfaces on a strict database — this test guards against the
        // regression once Postgres-backed tests are available.
        val mangaId = createLibraryManga("Vagabond")
        createChapters(mangaId, 5, read = true)
        createChapters(mangaId, 3, read = false, start = 6)

        // Default-category branch: manga with a MangaUserTable row must be listed without error.
        val defaultCategoryId = Category.getDefaultCategoryId(1)!!
        val defaultList = CategoryManga.getCategoryMangaList(1, defaultCategoryId)
        assertEquals(1, defaultList.size, "Default category should contain the library manga")
        assertEquals(3, defaultList[0].unreadCount, "Unread count should reflect the unread chapters")
        assertEquals(8, defaultList[0].chapterCount, "Chapter count should reflect all chapters")

        // Named-category branch: same query shape, different join order, must also stay valid.
        val categoryId = Category.createCategory(1, "Seinen")
        CategoryManga.addMangaToCategory(1, mangaId, categoryId)
        val namedList = CategoryManga.getCategoryMangaList(1, categoryId)
        assertEquals(1, namedList.size, "Named category should contain the moved manga")
        assertEquals(3, namedList[0].unreadCount, "Unread count should be preserved after moving")
    }

    @Test
    fun `duplicate manga-category pairing is rejected by the unique constraint`() {
        val mangaId = createLibraryManga("Naruto")
        val categoryId = Category.createCategory(1, "Shonen")
        CategoryManga.addMangaToCategory(1, mangaId, categoryId)

        // Bypass the application layer's own duplicate checks and attempt to insert the same
        // (manga, category) pairing directly. This must be rejected by the DB-level unique
        // constraint added in M0062_PreventDuplicatedCategoryManga. If that constraint is ever
        // dropped or weakened, this insert would succeed and silently inflate category/library
        // counts.
        assertFailsWith<ExposedSQLException> {
            transaction {
                CategoryMangaTable.insert {
                    it[CategoryMangaTable.manga] = mangaId
                    it[CategoryMangaTable.category] = categoryId
                    it[CategoryMangaTable.user] = 1
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
        val categoryId = Category.createCategory(1, "Adventure")

        // addMangasToCategories catches and swallows the unique constraint violation from a
        // duplicate (manga, category) pairing, so repeated calls should be no-ops rather than
        // throwing or creating duplicate CategoryMangaTable rows.
        CategoryManga.addMangasToCategories(1, listOf(mangaId), listOf(categoryId))
        CategoryManga.addMangasToCategories(1, listOf(mangaId), listOf(categoryId))

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
            CategoryManga.getCategoryMangaList(1, categoryId).size,
            "Category size should reflect a single manga even if it was added twice",
        )
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            ChapterTable,
            CategoryMangaTable,
            MangaTable,
        )
        transaction {
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
        }
    }
}
