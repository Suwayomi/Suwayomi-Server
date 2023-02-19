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
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Category.DEFAULT_CATEGORY_ID
import suwayomi.tachidesk.manga.impl.util.lang.isEmpty
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass

object CategoryManga {
    fun addMangaToCategory(mangaId: Int, categoryId: Int) {
        fun notAlreadyInCategory() = CategoryMangaTable.select { (CategoryMangaTable.category eq categoryId) and (CategoryMangaTable.manga eq mangaId) }.isEmpty()

        transaction {
            if (notAlreadyInCategory()) {
                CategoryMangaTable.insert {
                    it[CategoryMangaTable.category] = categoryId
                    it[CategoryMangaTable.manga] = mangaId
                }

                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[MangaTable.defaultCategory] = false
                }
            }
        }
    }

    fun removeMangaFromCategory(mangaId: Int, categoryId: Int) {
        transaction {
            CategoryMangaTable.deleteWhere { (CategoryMangaTable.category eq categoryId) and (CategoryMangaTable.manga eq mangaId) }
            if (CategoryMangaTable.select { CategoryMangaTable.manga eq mangaId }.count() == 0L) {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[MangaTable.defaultCategory] = true
                }
            }
        }
    }

    /**
     * list of mangas that belong to a category
     */
    fun getCategoryMangaList(categoryId: Int): List<MangaDataClass> {
        // Select the required columns from the MangaTable and add the aggregate functions to compute unread, download, and chapter counts
        val unreadCountEx = ChapterTable.isRead.count().alias("unread_count")
        val downloadedCount = ChapterTable.isDownloaded.count().alias("download_count")
        val chapterCount = ChapterTable.id.count().alias("chapter_count")
        val selectedColumns = MangaTable.columns + unreadCountEx + downloadedCount + chapterCount

        val transform: (ResultRow) -> MangaDataClass = {
            // Map the data from the result row to the MangaDataClass
            val dataClass = MangaTable.toDataClass(it)
            dataClass.lastChapterRead = ChapterTable.toDataClass(
                transaction {
                    ChapterTable
                        .select { (ChapterTable.manga eq it[MangaTable.id].value) }
                        .orderBy(ChapterTable.lastReadAt, SortOrder.DESC)
                        .first()
                }
            )
            dataClass.unreadCount = it[unreadCountEx]
            dataClass.downloadCount = it[downloadedCount]
            dataClass.chapterCount = it[chapterCount] as Long
            dataClass
        }

        return transaction {
            // Fetch data from the MangaTable and join with the CategoryMangaTable, if a category is specified
            val query = if (categoryId == DEFAULT_CATEGORY_ID) {
                MangaTable
                    .leftJoin(ChapterTable, { MangaTable.id }, { ChapterTable.manga })
                    .slice(columns = selectedColumns)
                    .select { (MangaTable.inLibrary eq true) and (MangaTable.defaultCategory eq true) }
            } else {
                MangaTable
                    .innerJoin(CategoryMangaTable)
                    .leftJoin(ChapterTable, { MangaTable.id }, { ChapterTable.manga })
                    .slice(columns = selectedColumns)
                    .select { (MangaTable.inLibrary eq true) and (CategoryMangaTable.category eq categoryId) }
            }

            // Join with the ChapterTable to fetch the last read chapter for each manga
            query.groupBy(*MangaTable.columns.toTypedArray()).map(transform)
        }
    }

    /**
     * list of categories that a manga belongs to
     */
    fun getMangaCategories(mangaId: Int): List<CategoryDataClass> {
        return transaction {
            CategoryMangaTable.innerJoin(CategoryTable).select { CategoryMangaTable.manga eq mangaId }.orderBy(CategoryTable.order to SortOrder.ASC).map {
                CategoryTable.toDataClass(it)
            }
        }
    }
}
