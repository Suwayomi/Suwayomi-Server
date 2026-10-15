package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
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
    fun backup(
        userId: Int,
        flags: BackupFlags,
    ): List<BackupCategory> =
        dbTransaction {
            val categories =
                CategoryTable
                    .selectAll()
                    .where { CategoryTable.user eq userId }
                    .orderBy(CategoryTable.order to SortOrder.ASC)
                    .toList()

            val categoryToMeta =
                if (flags.includeClientData) {
                    Category.getCategoriesMetaMaps(userId, categories.map { it[CategoryTable.id].value })
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
        userId: Int,
        backupCategories: List<BackupCategory>,
        syncMode: SyncRestoreMode = SyncRestoreMode.NONE,
    ): Map<Int, Int> {
        val dbCategories = Category.getCategoryList(userId, includeDefault = true)
        val dbCategoriesByName = dbCategories.associateBy { it.name }
        val dbCategoriesByUid = dbCategories.associateBy { it.uid }

        val defaultCategoryId = Category.getDefaultCategoryId(userId)

        var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

        // the wire is 0-based (Mihon/SY convention); store 1-based ranks instead of raw
        // orders — the default category never receives an ordinary rank
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
                        // the default category is created/managed by the server itself:
                        // restore points manga at it without ever renaming/reordering it
                        if (backupCategory.name.equals(Category.DEFAULT_CATEGORY_NAME, true)) {
                            return@mapIndexed defaultCategoryId
                        }

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
                                // only sync restores mark rows as in-flight
                                it[isSyncing] = syncMode.isSync
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
                                it[user] = userId
                                it[flags] = backupCategory.flags
                            }.value
                    }
            }

        Category.normalizeCategories(userId)

        if (syncMode.isSync) {
            transaction {
                CategoryTable.update({ CategoryTable.user eq userId and (CategoryTable.isSyncing eq true) }) {
                    it[isSyncing] = false
                }
            }
        }

        val metaEntryByCategoryId =
            categoryIds
                .zip(backupCategories)
                .mapNotNull { (categoryId, backupCategory) ->
                    categoryId?.let { it to backupCategory.meta }
                }.toMap()

        modifyCategoriesMetas(userId, metaEntryByCategoryId)

        // map by the backup's declared order (the join key manga.categories carries).
        // Duplicate orders resolve to the first occurrence's category — stable across
        // restores rather than silently remapped by map iteration order.
        val categoryMapping = LinkedHashMap<Int, Int>()
        backupCategories.forEachIndexed { index, backupCategory ->
            val categoryId = categoryIds[index] ?: return@forEachIndexed
            categoryMapping.putIfAbsent(backupCategory.order, categoryId)
        }
        return categoryMapping
    }
}
