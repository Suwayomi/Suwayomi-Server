package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.wrapAsExpression
import suwayomi.tachidesk.manga.impl.Category.DEFAULT_CATEGORY_ID
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.database.dbTransaction

object CategoryManga {
    fun addMangaToCategory(
        mangaId: Int,
        categoryId: Int,
    ) {
        addMangaToCategories(mangaId, listOf(categoryId))
    }

    fun addMangaToCategories(
        mangaId: Int,
        categoryIds: List<Int>,
    ) {
        addMangasToCategories(listOf(mangaId), categoryIds)
    }

    fun addMangasToCategories(
        mangaIds: List<Int>,
        categoryIds: List<Int>,
    ) {
        val filteredCategoryIds = categoryIds.filter { it != DEFAULT_CATEGORY_ID }

        val mangaIdsToCategoryIds = getMangasCategories(mangaIds).mapValues { it.value.map { category -> category.id } }
        val mangaIdsToNewCategoryIds =
            mangaIds.associateWith { mangaId ->
                filteredCategoryIds.filter { categoryId ->
                    !(mangaIdsToCategoryIds[mangaId]?.contains(categoryId) ?: false)
                }
            }

        val newMangaCategoryMappings =
            mangaIdsToNewCategoryIds.flatMap { (mangaId, newCategoryIds) ->
                newCategoryIds.map { mangaId to it }
            }

        dbTransaction {
            CategoryMangaTable.batchInsert(newMangaCategoryMappings) { (mangaId, categoryId) ->
                this[CategoryMangaTable.manga] = mangaId
                this[CategoryMangaTable.category] = categoryId
            }
        }
    }

    fun removeMangaFromCategory(
        mangaId: Int,
        categoryId: Int,
    ) {
        if (categoryId == DEFAULT_CATEGORY_ID) return
        transaction {
            CategoryMangaTable.deleteWhere { (CategoryMangaTable.category eq categoryId) and (CategoryMangaTable.manga eq mangaId) }
        }
    }

    /**
     * list of mangas that belong to a category
     */
    fun getCategoryMangaList(categoryId: Int): List<MangaDataClass> {
        // Select the required columns from the MangaTable and add the aggregate functions to compute unread, download, and chapter counts
        val unreadCount =
            wrapAsExpression<Long>(
                ChapterTable
                    .select(
                        ChapterTable.id.count(),
                    ).where { ((ChapterTable.isRead eq false) and (ChapterTable.manga eq MangaTable.id)) },
            )
        val downloadedCount =
            wrapAsExpression<Long>(
                ChapterTable
                    .select(
                        ChapterTable.id.count(),
                    ).where { ((ChapterTable.isDownloaded eq true) and (ChapterTable.manga eq MangaTable.id)) },
            )

        val chapterCount = ChapterTable.id.count().alias("chapter_count")
        val lastReadAt = ChapterTable.lastReadAt.max().alias("last_read_at")
        val selectedColumns = MangaTable.columns + unreadCount + downloadedCount + chapterCount + lastReadAt

        val transform: (ResultRow) -> MangaDataClass = {
            // Map the data from the result row to the MangaDataClass
            val dataClass = MangaTable.toDataClass(it)
            dataClass.lastReadAt = it[lastReadAt]
            dataClass.unreadCount = it[unreadCount]
            dataClass.downloadCount = it[downloadedCount]
            dataClass.chapterCount = it[chapterCount]
            dataClass
        }

        return transaction {
            // Fetch data from the MangaTable and join with the CategoryMangaTable, if a category is specified
            val query =
                if (categoryId == DEFAULT_CATEGORY_ID) {
                    MangaTable
                        .leftJoin(ChapterTable, { MangaTable.id }, { ChapterTable.manga })
                        .leftJoin(CategoryMangaTable)
                        .select(columns = selectedColumns)
                        .where { (MangaTable.inLibrary eq true) and CategoryMangaTable.category.isNull() }
                } else {
                    MangaTable
                        .innerJoin(CategoryMangaTable)
                        .leftJoin(ChapterTable, { MangaTable.id }, { ChapterTable.manga })
                        .select(columns = selectedColumns)
                        .where { (MangaTable.inLibrary eq true) and (CategoryMangaTable.category eq categoryId) }
                }

            // Join with the ChapterTable to fetch the last read chapter for each manga
            query.groupBy(*MangaTable.columns.toTypedArray()).map(transform)
        }
    }

    /**
     * list of categories that a manga belongs to
     */
    fun getMangaCategories(mangaId: Int): List<CategoryDataClass> =
        transaction {
            CategoryMangaTable
                .innerJoin(CategoryTable)
                .selectAll()
                .where {
                    CategoryMangaTable.manga eq mangaId
                }.orderBy(CategoryTable.order to SortOrder.ASC)
                .map {
                    CategoryTable.toDataClass(it)
                }
        }

    fun getMangasCategories(mangaIDs: List<Int>): Map<Int, List<CategoryDataClass>> =
        buildMap {
            transaction {
                CategoryMangaTable
                    .innerJoin(CategoryTable)
                    .selectAll()
                    .where { CategoryMangaTable.manga inList mangaIDs }
                    .groupBy { it[CategoryMangaTable.manga] }
                    .forEach {
                        val mangaId = it.key.value
                        val categories = it.value

                        set(mangaId, categories.map { category -> CategoryTable.toDataClass(category) })
                    }
            }
        }
}
