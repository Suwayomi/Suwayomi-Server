package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.Category.modifyCategoriesMetas
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.database.dbTransaction

object BackupCategoryHandler {
    fun backup(flags: BackupFlags): List<BackupCategory> =
        dbTransaction {
            val categories =
                CategoryTable
                    .selectAll()
                    .orderBy(CategoryTable.order to SortOrder.ASC)
                    .map { CategoryTable.toDataClass(it) }

            val categoryToMeta =
                if (flags.includeClientData) {
                    Category.getCategoriesMetaMaps(categories.map { it.id })
                } else {
                    emptyMap()
                }

            categories.map {
                BackupCategory(
                    it.name,
                    it.order,
                    0, // not supported in Tachidesk
                    it.version,
                    it.uid,
                    it.lastModifiedAt,
                ).apply {
                    this.meta = categoryToMeta[it.id] ?: emptyMap()
                }
            }
        }

    fun restore(backupCategories: List<BackupCategory>): Map<Int, Int> {
        val dbCategories = Category.getCategoryList()
        val dbCategoriesByName = dbCategories.associateBy { it.name }
        val dbCategoriesByUid = dbCategories.associateBy { it.uid }

        var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

        val categoryIds =
            transaction {
                backupCategories
                    .map { backupCategory ->
                        var dbCategory =
                            if (backupCategory.uid != 0L) {
                                dbCategoriesByUid[backupCategory.uid]
                            } else {
                                null
                            }

                        if (dbCategory == null) {
                            dbCategory = dbCategoriesByName[backupCategory.name]
                        }

                        if (dbCategory != null) {
                            CategoryTable.update({ CategoryTable.id eq dbCategory.id }) {
                                it[name] = backupCategory.name
                                it[order] = backupCategory.order
                                it[version] = backupCategory.version
                                it[uid] = if (backupCategory.uid != 0L) backupCategory.uid else dbCategory.uid
                                it[lastModifiedAt] = backupCategory.lastModifiedAt
                                it[isSyncing] = true
                            }
                            return@map dbCategory.id
                        }

                        val currentOrder = nextOrder++
                        CategoryTable
                            .insertAndGetId {
                                it[name] = backupCategory.name
                                it[order] = currentOrder
                                it[version] = backupCategory.version
                                it[uid] = backupCategory.uid
                                it[lastModifiedAt] = backupCategory.lastModifiedAt
                            }.value
                    }
            }

        transaction {
            CategoryTable.update({ CategoryTable.isSyncing eq true }) {
                it[isSyncing] = false
            }
        }

        val metaEntryByCategoryId =
            categoryIds
                .zip(backupCategories)
                .associate { (categoryId, backupCategory) ->
                    categoryId to backupCategory.meta
                }

        modifyCategoriesMetas(metaEntryByCategoryId)

        return backupCategories.withIndex().associate { (index, backupCategory) ->
            backupCategory.order to categoryIds[index]
        }
    }
}
