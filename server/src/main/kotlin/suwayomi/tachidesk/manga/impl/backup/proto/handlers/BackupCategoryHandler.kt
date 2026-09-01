package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.Category.modifyCategoriesMetas
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.SyncRestoreMode
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.server.database.dbTransaction

object BackupCategoryHandler {
    fun backup(flags: BackupFlags): List<BackupCategory> =
        dbTransaction {
            val categories =
                CategoryTable
                    .selectAll()
                    .orderBy(CategoryTable.order to SortOrder.ASC)
                    .toList()

            val categoryToMeta =
                if (flags.includeClientData) {
                    Category.getCategoriesMetaMaps(categories.map { it[CategoryTable.id].value })
                } else {
                    emptyMap()
                }

            categories.map {
                BackupCategory(
                    it[CategoryTable.name],
                    it[CategoryTable.order],
                    it[CategoryTable.flags],
                    it[CategoryTable.version],
                    it[CategoryTable.uid],
                    it[CategoryTable.lastModifiedAt],
                ).apply {
                    this.meta = categoryToMeta[it[CategoryTable.id].value] ?: emptyMap()
                }
            }
        }

    fun restore(
        backupCategories: List<BackupCategory>,
        syncMode: SyncRestoreMode = SyncRestoreMode.NONE,
    ): Map<Int, Int> {
        val dbCategories = Category.getCategoryList()
        val dbCategoriesByName = dbCategories.associateBy { it.name }
        val dbCategoriesByUid = dbCategories.associateBy { it.uid }

        var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

        // the wire is 0-based (Mihon/SY convention); store 1-based ranks instead of raw orders
        val ranks = IntArray(backupCategories.size)
        backupCategories
            .withIndex()
            .filter { (_, backupCategory) -> !backupCategory.name.equals(Category.DEFAULT_CATEGORY_NAME, true) }
            .sortedBy { (_, backupCategory) -> backupCategory.order }
            .forEachIndexed { rank, (index, _) -> ranks[index] = rank + 1 }

        val categoryIds =
            transaction {
                backupCategories
                    .mapIndexed { index, backupCategory ->
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
                            // a newer local copy (pending reorder/rename) wins the next upload
                            if (syncMode == SyncRestoreMode.ADOPT && backupCategory.version < dbCategory.version) {
                                return@mapIndexed dbCategory.id
                            }
                            CategoryTable.update({ CategoryTable.id eq dbCategory.id }) {
                                it[name] = backupCategory.name
                                it[order] = ranks[index]
                                it[version] = backupCategory.version
                                it[uid] = if (backupCategory.uid != 0L) backupCategory.uid else dbCategory.uid
                                it[lastModifiedAt] = backupCategory.lastModifiedAt
                                // outside ADOPT a zeroed backup must not wipe stored flags
                                if (syncMode == SyncRestoreMode.ADOPT || backupCategory.flags != 0) {
                                    it[flags] = backupCategory.flags
                                }
                                it[isSyncing] = true
                            }
                            return@mapIndexed dbCategory.id
                        }

                        // sync mirrors the server list; a plain restore appends new categories at the end
                        val currentOrder = if (syncMode.isSync) ranks[index] else nextOrder++
                        CategoryTable
                            .insertAndGetId {
                                it[name] = backupCategory.name
                                it[order] = currentOrder
                                it[version] = backupCategory.version
                                it[uid] = backupCategory.uid
                                it[lastModifiedAt] = backupCategory.lastModifiedAt
                                it[flags] = backupCategory.flags
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
