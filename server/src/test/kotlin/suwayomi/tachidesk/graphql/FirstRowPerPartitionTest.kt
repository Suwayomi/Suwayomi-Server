package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.graphql.server.primitives.firstRowPerPartition
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables

/** [firstRowPerPartition] isn't tied to chapters: here, the alphabetically last manga of each category. */
class FirstRowPerPartitionTest : ApplicationTest() {
    private val createdCategories = mutableListOf<Int>()

    private fun insertCategory(name: String): Int =
        CategoryTable
            .insertAndGetId {
                it[CategoryTable.name] = name
                it[user] = 1
            }.value
            .also { createdCategories += it }

    private fun insertManga(
        title: String,
        categoryId: Int,
    ): Int {
        val mangaId =
            MangaTable
                .insertAndGetId {
                    it[MangaTable.title] = title
                    it[url] = "$categoryId/$title"
                    it[sourceReference] = 1L
                }.value
        CategoryMangaTable.insert {
            it[category] = categoryId
            it[manga] = mangaId
            it[user] = 1
        }
        return mangaId
    }

    private fun lastMangaPerCategory(categoryIds: List<Int>) =
        CategoryMangaTable
            .innerJoin(MangaTable)
            .firstRowPerPartition(
                keys = CategoryTable.select(CategoryTable.id).where { CategoryTable.id inList categoryIds },
                partitionBy = CategoryMangaTable.category,
                idColumn = MangaTable.id,
                orderBy = listOf(MangaTable.title to SortOrder.DESC),
            ).associate { it[CategoryMangaTable.category].value to it[MangaTable.id].value }

    @Test
    fun `picks the first row of each partition in a single query`() {
        val (expected, categories) =
            transaction {
                val categoryA = insertCategory("A")
                val categoryB = insertCategory("B")
                val empty = insertCategory("Empty")
                insertManga("Apple", categoryA)
                val lastOfA = insertManga("Zebra", categoryA)
                insertManga("Mango", categoryA)
                val onlyOfB = insertManga("Kiwi", categoryB)
                mapOf(categoryA to lastOfA, categoryB to onlyOfB) to listOf(categoryA, categoryB, empty)
            }

        val statements = mutableListOf<String>()
        val lastByCategory =
            transaction {
                addLogger(
                    object : SqlLogger {
                        override fun log(
                            context: StatementContext,
                            transaction: Transaction,
                        ) {
                            statements += context.sql(transaction)
                        }
                    },
                )
                lastMangaPerCategory(categories)
            }

        assertEquals(expected, lastByCategory)
        assertEquals(1, statements.size, "expected a single query, ran $statements")
    }

    @Test
    fun `ties go to the lowest id`() {
        val (categoryId, firstId) =
            transaction {
                val categoryId = insertCategory("A")
                val first = insertManga("Same", categoryId)
                insertManga("Same", categoryId)
                categoryId to first
            }

        assertEquals(mapOf(categoryId to firstId), transaction { lastMangaPerCategory(listOf(categoryId)) })
    }

    @Test
    fun `filters the rows of each partition`() {
        val (categoryId, kiwi) =
            transaction {
                val categoryId = insertCategory("A")
                insertManga("Zebra", categoryId)
                categoryId to insertManga("Kiwi", categoryId)
            }

        val lastWithoutZebra =
            transaction {
                CategoryMangaTable
                    .innerJoin(MangaTable)
                    .firstRowPerPartition(
                        keys = CategoryTable.select(CategoryTable.id).where { CategoryTable.id eq categoryId },
                        partitionBy = CategoryMangaTable.category,
                        idColumn = MangaTable.id,
                        orderBy = listOf(MangaTable.title to SortOrder.DESC),
                        where = MangaTable.title neq "Zebra",
                    ).map { it[MangaTable.id].value }
            }

        assertEquals(listOf(kiwi), lastWithoutZebra)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(CategoryMangaTable, MangaTable)
        // other tests rely on each user's default category, so only these go
        transaction { CategoryTable.deleteWhere { CategoryTable.id inList createdCategories } }
        createdCategories.clear()
    }
}
