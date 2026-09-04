package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.database.dbTransaction

object CategoryManga {
    fun addMangaToCategory(
        userId: Int,
        mangaId: Int,
        categoryId: Int,
    ) {
        addMangaToCategories(userId, mangaId, listOf(categoryId))
    }

    fun addMangaToCategories(
        userId: Int,
        mangaId: Int,
        categoryIds: List<Int>,
    ) {
        addMangasToCategories(userId, listOf(mangaId), categoryIds)
    }

    fun addMangasToCategories(
        userId: Int,
        mangaIds: List<Int>,
        categoryIds: List<Int>,
    ) {
        val defaultCategoryId = Category.getDefaultCategoryId(userId)
        val filteredCategoryIds = categoryIds.filter { it != defaultCategoryId }

        val mangaIdsToCategoryIds = getMangasCategories(userId, mangaIds).mapValues { it.value.map { category -> category.id } }
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
            CategoryMangaTable.batchUpsert(
                newMangaCategoryMappings,
                CategoryMangaTable.manga,
                CategoryMangaTable.category,
            ) { (mangaId, categoryId) ->
                this[CategoryMangaTable.manga] = mangaId
                this[CategoryMangaTable.category] = categoryId
                this[CategoryMangaTable.user] = userId
            }
        }
    }

    fun removeMangaFromCategory(
        userId: Int,
        mangaId: Int,
        categoryId: Int,
    ) {
        if (categoryId == Category.getDefaultCategoryId(userId)) return
        transaction {
            CategoryMangaTable.deleteWhere {
                (CategoryMangaTable.category eq categoryId) and
                    (CategoryMangaTable.manga eq mangaId) and
                    (CategoryMangaTable.user eq userId)
            }
        }
    }

    fun removeMangaFromAllCategories(
        userId: Int,
        mangaId: Int,
    ) {
        transaction {
            CategoryMangaTable.deleteWhere { (CategoryMangaTable.user eq userId) and (CategoryMangaTable.manga eq mangaId) }
        }
    }

    /**
     * list of mangas that belong to a category
     */
    fun getCategoryMangaList(
        userId: Int,
        categoryId: Int,
    ): List<MangaDataClass> {
        // Select the required columns from the MangaTable and add the aggregate functions to compute unread, download, and chapter counts
        val unreadCount =
            wrapAsExpression<Long>(
                ChapterTable
                    .getWithUserData(userId)
                    .select(ChapterTable.id.count())
                    .where {
                        (ChapterUserTable.isRead eq false or (ChapterUserTable.isRead.isNull())) and
                            (ChapterTable.manga eq MangaTable.id)
                    },
            )
        val downloadedCount =
            wrapAsExpression<Long>(
                ChapterTable
                    .getWithUserData(userId)
                    .select(ChapterTable.id.count())
                    .where { (ChapterUserTable.isDownloaded eq true) and (ChapterTable.manga eq MangaTable.id) },
            )

        val chapterCount = ChapterTable.id.count().alias("chapter_count")
        val lastReadAt = ChapterUserTable.lastReadAt.max().alias("last_read_at")
        val selectedColumns = MangaTable.getWithUserData(userId).columns + unreadCount + downloadedCount + chapterCount + lastReadAt

        val transform: (ResultRow) -> MangaDataClass = {
            // Map the data from the result row to the MangaDataClass
            MangaTable
                .toDataClass(it)
                .copy(
                    lastReadAt = it[lastReadAt],
                    unreadCount = it[unreadCount],
                    downloadCount = it[downloadedCount],
                    chapterCount = it[chapterCount],
                )
        }

        return transaction {
            // Fetch data from the MangaTable and join with the CategoryMangaTable, if a category is specified
            val query =
                if (categoryId == Category.getDefaultCategoryId(userId)) {
                    MangaTable
                        .getWithUserData(userId)
                        .leftJoin(
                            ChapterTable.getWithUserData(userId),
                            { MangaTable.id },
                            { ChapterTable.manga },
                        ).leftJoin(
                            CategoryMangaTable,
                            onColumn = { MangaTable.id },
                            otherColumn = { CategoryMangaTable.manga },
                            additionalConstraint = { CategoryMangaTable.user eq userId },
                        ).select(columns = selectedColumns)
                        .where {
                            (MangaUserTable.inLibrary eq true) and
                                CategoryMangaTable.category.isNull()
                        }
                } else {
                    MangaTable
                        .getWithUserData(userId)
                        .leftJoin(
                            CategoryMangaTable,
                            onColumn = { MangaTable.id },
                            otherColumn = { CategoryMangaTable.manga },
                            additionalConstraint = { CategoryMangaTable.user eq userId },
                        ).leftJoin(
                            ChapterTable.getWithUserData(userId),
                            { MangaTable.id },
                            { ChapterTable.manga },
                        ).select(columns = selectedColumns)
                        .where { (MangaUserTable.inLibrary eq true) and (CategoryMangaTable.category eq categoryId) }
                }

            // Join with the ChapterTable to fetch the last read chapter for each manga
            query.groupBy(*MangaTable.columns.toTypedArray()).map(transform)
        }
    }

    /**
     * list of categories that a manga belongs to
     */
    fun getMangaCategories(
        userId: Int,
        mangaId: Int,
    ): List<CategoryDataClass> =
        transaction {
            CategoryMangaTable
                .innerJoin(CategoryTable)
                .selectAll()
                .where {
                    CategoryMangaTable.manga eq mangaId and (CategoryTable.user eq userId)
                }.orderBy(CategoryTable.order to SortOrder.ASC)
                .map {
                    CategoryTable.toDataClass(it)
                }
        }

    fun getMangasCategories(
        userId: Int,
        mangaIDs: List<Int>,
    ): Map<Int, List<CategoryDataClass>> =
        buildMap {
            transaction {
                CategoryMangaTable
                    .innerJoin(
                        CategoryTable,
                        onColumn = { CategoryMangaTable.category },
                        otherColumn = { CategoryTable.id },
                        additionalConstraint = { CategoryTable.user eq userId },
                    ).selectAll()
                    .where {
                        (CategoryMangaTable.user eq userId) and
                            (CategoryMangaTable.manga inList mangaIDs)
                    }.groupBy { it[CategoryMangaTable.manga] }
                    .forEach {
                        val mangaId = it.key.value
                        val categories = it.value

                        set(mangaId, categories.map { category -> CategoryTable.toDataClass(category) })
                    }
            }
        }
}
