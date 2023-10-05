package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import suwayomi.tachidesk.manga.model.table.ChapterTable

@Suppress("ClassName", "unused")
class M0021_GlobalAndCategoryMeta : AddTableMigration() {
    private class GlobalMetaTable : IntIdTable() {
        val key = varchar("key", 256)
        val value = varchar("value", 4096)
    }

    private class CategoryMetaTable : IntIdTable() {
        val key = varchar("key", 256)
        val value = varchar("value", 4096)
        val ref = reference("category_ref", ChapterTable, ReferenceOption.CASCADE)
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                GlobalMetaTable(),
                CategoryMetaTable(),
            )
}
