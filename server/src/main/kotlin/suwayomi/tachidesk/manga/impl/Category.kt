package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass

object Category {
    /**
     * The new category will be placed at the end of the list
     */
    fun createCategory(name: String): Int {
        // creating a category named Default is illegal
        if (name.equals(DEFAULT_CATEGORY_NAME, ignoreCase = true)) return -1

        return transaction {
            if (CategoryTable.select { CategoryTable.name eq name }.firstOrNull() == null) {
                val newCategoryId =
                    CategoryTable.insertAndGetId {
                        it[CategoryTable.name] = name
                        it[CategoryTable.order] = Int.MAX_VALUE
                    }.value

                normalizeCategories()

                newCategoryId
            } else {
                -1
            }
        }
    }

    fun updateCategory(
        categoryId: Int,
        name: String?,
        isDefault: Boolean?,
        includeInUpdate: Int?,
        includeInDownload: Int?,
    ) {
        transaction {
            CategoryTable.update({ CategoryTable.id eq categoryId }) {
                if (
                    categoryId != DEFAULT_CATEGORY_ID && name != null &&
                    !name.equals(DEFAULT_CATEGORY_NAME, ignoreCase = true)
                ) {
                    it[CategoryTable.name] = name
                }
                if (categoryId != DEFAULT_CATEGORY_ID && isDefault != null) it[CategoryTable.isDefault] = isDefault
                if (includeInUpdate != null) it[CategoryTable.includeInUpdate] = includeInUpdate
                if (includeInDownload != null) it[CategoryTable.includeInDownload] = includeInDownload
            }
        }
    }

    /**
     * Move the category from order number `from` to `to`
     */
    fun reorderCategory(
        from: Int,
        to: Int,
    ) {
        if (from == 0 || to == 0) return
        transaction {
            val categories =
                CategoryTable.select {
                    CategoryTable.id neq DEFAULT_CATEGORY_ID
                }.orderBy(CategoryTable.order to SortOrder.ASC).toMutableList()
            categories.add(to - 1, categories.removeAt(from - 1))
            categories.forEachIndexed { index, cat ->
                CategoryTable.update({ CategoryTable.id eq cat[CategoryTable.id].value }) {
                    it[CategoryTable.order] = index + 1
                }
            }
            normalizeCategories()
        }
    }

    fun removeCategory(categoryId: Int) {
        if (categoryId == DEFAULT_CATEGORY_ID) return
        transaction {
            CategoryTable.deleteWhere { CategoryTable.id eq categoryId }
            normalizeCategories()
        }
    }

    /** make sure category order numbers starts from 1 and is consecutive */
    fun normalizeCategories() {
        transaction {
            CategoryTable.selectAll()
                .orderBy(CategoryTable.order to SortOrder.ASC)
                .sortedWith(compareBy({ it[CategoryTable.id].value != 0 }, { it[CategoryTable.order] }))
                .forEachIndexed { index, cat ->
                    CategoryTable.update({ CategoryTable.id eq cat[CategoryTable.id].value }) {
                        it[CategoryTable.order] = index
                    }
                }
        }
    }

    private fun needsDefaultCategory() =
        transaction {
            MangaTable
                .leftJoin(CategoryMangaTable)
                .select { MangaTable.inLibrary eq true }
                .andWhere { CategoryMangaTable.manga.isNull() }
                .empty()
                .not()
        }

    const val DEFAULT_CATEGORY_ID = 0
    const val DEFAULT_CATEGORY_NAME = "Default"

    fun getCategoryList(): List<CategoryDataClass> {
        return transaction {
            CategoryTable.selectAll()
                .orderBy(CategoryTable.order to SortOrder.ASC)
                .let {
                    if (needsDefaultCategory()) {
                        it
                    } else {
                        it.andWhere { CategoryTable.id neq DEFAULT_CATEGORY_ID }
                    }
                }
                .map {
                    CategoryTable.toDataClass(it)
                }
        }
    }

    fun getCategoryById(categoryId: Int): CategoryDataClass? {
        return transaction {
            CategoryTable.select { CategoryTable.id eq categoryId }.firstOrNull()?.let {
                CategoryTable.toDataClass(it)
            }
        }
    }

    fun getCategorySize(categoryId: Int): Int {
        return transaction {
            if (categoryId == DEFAULT_CATEGORY_ID) {
                MangaTable
                    .leftJoin(CategoryMangaTable)
                    .select { MangaTable.inLibrary eq true }
                    .andWhere { CategoryMangaTable.manga.isNull() }
            } else {
                CategoryMangaTable.leftJoin(MangaTable).select { CategoryMangaTable.category eq categoryId }
                    .andWhere { MangaTable.inLibrary eq true }
            }.count().toInt()
        }
    }

    fun getCategoryMetaMap(categoryId: Int): Map<String, String> {
        return transaction {
            CategoryMetaTable.select { CategoryMetaTable.ref eq categoryId }
                .associate { it[CategoryMetaTable.key] to it[CategoryMetaTable.value] }
        }
    }

    fun modifyMeta(
        categoryId: Int,
        key: String,
        value: String,
    ) {
        transaction {
            val meta =
                transaction {
                    CategoryMetaTable.select { (CategoryMetaTable.ref eq categoryId) and (CategoryMetaTable.key eq key) }
                }.firstOrNull()

            if (meta == null) {
                CategoryMetaTable.insert {
                    it[CategoryMetaTable.key] = key
                    it[CategoryMetaTable.value] = value
                    it[CategoryMetaTable.ref] = categoryId
                }
            } else {
                CategoryMetaTable.update({ (CategoryMetaTable.ref eq categoryId) and (CategoryMetaTable.key eq key) }) {
                    it[CategoryMetaTable.value] = value
                }
            }
        }
    }
}
