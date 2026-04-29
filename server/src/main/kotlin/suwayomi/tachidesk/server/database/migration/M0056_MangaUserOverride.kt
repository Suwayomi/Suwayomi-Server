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
class M0056_MangaUserOverride : AddTableMigration() {
    private class MangaUserOverrideTable : IntIdTable() {
        val mangaRef = reference("manga_ref", MangaTable, ReferenceOption.CASCADE).uniqueIndex()
        val title = varchar("title", 512).nullable()
        val author = varchar("author", 256).nullable()
        val artist = varchar("artist", 256).nullable()
        val description = text("description").nullable()
        val genre = text("genre").nullable()
        val notes = text("notes").nullable()
        val hasCustomCover = bool("has_custom_cover").default(false)
        val createdAt = long("created_at")
        val updatedAt = long("updated_at")
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                MangaUserOverrideTable(),
            )
}
