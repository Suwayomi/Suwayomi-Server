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
import suwayomi.tachidesk.manga.model.table.MangaTable

@Suppress("ClassName", "unused")
class M0058_MangaKindleConfig : AddTableMigration() {
    private class MangaKindleConfigTable : IntIdTable() {
        val mangaRef = reference("manga_ref", MangaTable, ReferenceOption.CASCADE).uniqueIndex()
        val autoSend = bool("auto_send").default(false)
        val destination = varchar("destination", 320).nullable()
        val createdAt = long("created_at")
        val updatedAt = long("updated_at")
    }

    override val tables: Array<Table>
        get() = arrayOf(MangaKindleConfigTable())
}
