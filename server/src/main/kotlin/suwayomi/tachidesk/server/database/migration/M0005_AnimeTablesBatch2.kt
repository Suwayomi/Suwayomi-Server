@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table

@Suppress("ClassName", "unused")
class M0005_AnimeTablesBatch2 : AddTableMigration() {
    private class AnimeTable : IntIdTable() {
        val url = varchar("url", 2048)
        val title = varchar("title", 512)
        val initialized = bool("initialized").default(false)

        val artist = varchar("artist", 64).nullable()
        val author = varchar("author", 64).nullable()
        val description = varchar("description", 4096).nullable()
        val genre = varchar("genre", 1024).nullable()

        //    val status = enumeration("status", MangaStatus::class).default(MangaStatus.UNKNOWN)
        val status = integer("status").default(0)
        val thumbnail_url = varchar("thumbnail_url", 2048).nullable()

        val inLibrary = bool("in_library").default(false)
        val defaultCategory = bool("default_category").default(true)

        // source is used by some ancestor of IntIdTable
        val sourceReference = long("source")
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                AnimeTable(),
            )
}
