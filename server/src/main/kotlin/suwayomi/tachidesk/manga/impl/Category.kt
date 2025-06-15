package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import kotlin.collections.component1
import kotlin.collections.orEmpty

object Category {
    /**
     * The new category will be placed at the end of the list
     */
    fun createCategory(name: String): Int = createCategories(listOf(name)).first()

    fun createCategories(names: List<String>): List<Int> =
        transaction {
            val categoryIdToName = getCategoryList().associate { it.id to it.name.lowercase() }

            val categoriesToCreate =
                names
                    .filter {
                        !it.equals(DEFAULT_CATEGORY_NAME, true)
                    }.filter { !categoryIdToName.values.contains(it.lowercase()) }

            val newCategoryIdsByName =
                CategoryTable
                    .batchInsert(categoriesToCreate) {
                        this[CategoryTable.name] = it
                        this[CategoryTable.order] = Int.MAX_VALUE
                    }.associate { it[CategoryTable.name] to it[CategoryTable.id].value }

            normalizeCategories()

            names.map {
                // creating a category named Default is illegal
                if (it.equals(DEFAULT_CATEGORY_NAME, true)) {
                    DEFAULT_CATEGORY_ID
                } else {
                    newCategoryIdsByName[it] ?: categoryIdToName.entries.find { (_, name) -> name.equals(it, true) }!!.key
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
                    categoryId != DEFAULT_CATEGORY_ID &&
                    name != null &&
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
                CategoryTable
                    .selectAll()
                    .where {
                        CategoryTable.id neq DEFAULT_CATEGORY_ID
                    }.orderBy(CategoryTable.order to SortOrder.ASC)
                    .toMutableList()
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
            CategoryTable
                .selectAll()
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
                .selectAll()
                .where { MangaTable.inLibrary eq true }
                .andWhere { CategoryMangaTable.manga.isNull() }
                .empty()
                .not()
        }

    const val DEFAULT_CATEGORY_ID = 0
    const val DEFAULT_CATEGORY_NAME = "Default"

    fun getCategoryList(): List<CategoryDataClass> =
        transaction {
            CategoryTable
                .selectAll()
                .orderBy(CategoryTable.order to SortOrder.ASC)
                .let {
                    if (needsDefaultCategory()) {
                        it
                    } else {
                        it.andWhere { CategoryTable.id neq DEFAULT_CATEGORY_ID }
                    }
                }.map {
                    CategoryTable.toDataClass(it)
                }
        }

    fun getCategoryById(categoryId: Int): CategoryDataClass? =
        transaction {
            CategoryTable.selectAll().where { CategoryTable.id eq categoryId }.firstOrNull()?.let {
                CategoryTable.toDataClass(it)
            }
        }

    fun getCategorySize(categoryId: Int): Int =
        transaction {
            if (categoryId == DEFAULT_CATEGORY_ID) {
                MangaTable
                    .leftJoin(CategoryMangaTable)
                    .selectAll()
                    .where { MangaTable.inLibrary eq true }
                    .andWhere { CategoryMangaTable.manga.isNull() }
            } else {
                CategoryMangaTable
                    .leftJoin(MangaTable)
                    .selectAll()
                    .where { CategoryMangaTable.category eq categoryId }
                    .andWhere { MangaTable.inLibrary eq true }
            }.count().toInt()
        }

    fun getCategoryMetaMap(categoryId: Int): Map<String, String> =
        transaction {
            CategoryMetaTable
                .selectAll()
                .where { CategoryMetaTable.ref eq categoryId }
                .associate { it[CategoryMetaTable.key] to it[CategoryMetaTable.value] }
        }

    fun getCategoriesMetaMaps(ids: List<Int>): Map<Int, Map<String, String>> =
        transaction {
            CategoryMetaTable
                .selectAll()
                .where { CategoryMetaTable.ref inList ids }
                .groupBy { it[CategoryMetaTable.ref].value }
                .mapValues { it.value.associate { it[CategoryMetaTable.key] to it[CategoryMetaTable.value] } }
                .withDefault { emptyMap() }
        }

    fun modifyMeta(
        categoryId: Int,
        key: String,
        value: String,
    ) {
        modifyCategoriesMetas(mapOf(categoryId to mapOf(key to value)))
    }

    fun modifyCategoriesMetas(metaByCategoryId: Map<Int, Map<String, String>>) {
        transaction {
            val categoryIds = metaByCategoryId.keys
            val metaKeys = metaByCategoryId.flatMap { it.value.keys }

            val dbMetaByCategoryId =
                CategoryMetaTable
                    .selectAll()
                    .where { (CategoryMetaTable.ref inList categoryIds) and (CategoryMetaTable.key inList metaKeys) }
                    .groupBy { it[CategoryMetaTable.ref].value }

            val existingMetaByMetaId =
                categoryIds.flatMap { categoryId ->
                    val dbMetaByKey = dbMetaByCategoryId[categoryId].orEmpty().associateBy { it[CategoryMetaTable.key] }
                    val existingMetas = metaByCategoryId[categoryId].orEmpty().filter { (key) -> key in dbMetaByKey.keys }

                    existingMetas.map { entry ->
                        val metaId = dbMetaByKey[entry.key]!![CategoryMetaTable.id].value

                        metaId to entry
                    }
                }

            val newMetaByCategoryId =
                categoryIds.flatMap { categoryID ->
                    val dbMetaByKey = dbMetaByCategoryId[categoryID].orEmpty().associateBy { it[CategoryMetaTable.key] }

                    metaByCategoryId[categoryID]
                        .orEmpty()
                        .filter { entry -> entry.key !in dbMetaByKey.keys }
                        .map { entry -> categoryID to entry }
                }

            if (existingMetaByMetaId.isNotEmpty()) {
                BatchUpdateStatement(CategoryMetaTable).apply {
                    existingMetaByMetaId.forEach { (metaId, entry) ->
                        addBatch(EntityID(metaId, CategoryMetaTable))
                        this[CategoryMetaTable.value] = entry.value
                    }
                    execute(this@transaction)
                }
            }

            if (newMetaByCategoryId.isNotEmpty()) {
                CategoryMetaTable.batchInsert(newMetaByCategoryId) { (categoryId, entry) ->
                    this[CategoryMetaTable.ref] = EntityID(categoryId, CategoryTable)
                    this[CategoryMetaTable.key] = entry.key
                    this[CategoryMetaTable.value] = entry.value
                }
            }
        }
    }
}
