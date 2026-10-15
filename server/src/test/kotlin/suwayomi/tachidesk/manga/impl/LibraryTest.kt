package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.ensureDefaultCategory
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryTest : ApplicationTest() {
    private val createdUserIds = mutableListOf<Int>()
    private val createdMangaIds = mutableListOf<Int>()

    private fun createUser(name: String): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[UserAccountTable.username] = name
                    it[UserAccountTable.password] = "password"
                }.value
        }.also { createdUserIds += it }

    private fun createManga(title: String): Int =
        transaction {
            MangaTable
                .insertAndGetId {
                    it[MangaTable.title] = title
                    it[MangaTable.url] = title
                    it[MangaTable.sourceReference] = 1
                }.value
        }.also { createdMangaIds += it }

    @Test
    fun `addMangaToLibrary assigns default categories even when another user already categorized the manga`() {
        val userA = createUser("library-user-a")
        val userB = createUser("library-user-b")
        ensureDefaultCategory(userA)
        ensureDefaultCategory(userB)

        // a user-marked default category for B (is_default=true, not the system row)
        val defaultCategoryB = Category.createCategory(userB, "B default")
        Category.updateCategory(userB, defaultCategoryB, name = null, isDefault = true, includeInUpdate = null, includeInDownload = null)

        val mangaId = createManga("shared-manga")

        // A already categorized the manga (with a non-default category)
        val categoryA = Category.createCategory(userA, "A category")
        transaction {
            MangaUserTable.insert {
                it[MangaUserTable.manga] = mangaId
                it[MangaUserTable.user] = userA
                it[MangaUserTable.inLibrary] = true
            }
            CategoryMangaTable.insert {
                it[CategoryMangaTable.category] = categoryA
                it[CategoryMangaTable.manga] = mangaId
                it[CategoryMangaTable.user] = userA
            }
        }

        // B adds the same manga: A's CategoryManga rows must not suppress B's defaults
        Library.addMangaToLibrary(userB, mangaId)

        val rowsForB =
            transaction {
                CategoryMangaTable
                    .selectAll()
                    .where { CategoryMangaTable.manga eq mangaId and (CategoryMangaTable.user eq userB) }
                    .map { it[CategoryMangaTable.category].value }
            }
        assertEquals(listOf(defaultCategoryB), rowsForB)

        // A's association is untouched
        val rowsForA =
            transaction {
                CategoryMangaTable
                    .selectAll()
                    .where { CategoryMangaTable.manga eq mangaId and (CategoryMangaTable.user eq userA) }
                    .map { it[CategoryMangaTable.category].value }
            }
        assertEquals(listOf(categoryA), rowsForA)
    }

    @AfterEach
    fun tearDown() {
        clearTables(CategoryMangaTable, CategoryTable, MangaUserTable, MangaTable)
        transaction {
            if (createdUserIds.isNotEmpty()) {
                UserAccountTable.deleteWhere { UserAccountTable.id inList createdUserIds }
                createdUserIds.clear()
            }
        }
        createdMangaIds.clear()
    }
}
