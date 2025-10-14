package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
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
                ).apply {
                    this.meta = categoryToMeta[it.id] ?: emptyMap()
                }
            }
        }

    fun restore(backupCategories: List<BackupCategory>): Map<Int, Int> {
        val categoryIds = Category.createCategories(backupCategories.map { it.name })

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
