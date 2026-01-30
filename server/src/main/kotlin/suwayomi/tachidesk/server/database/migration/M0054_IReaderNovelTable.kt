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
class M0054_IReaderNovelTable : AddTableMigration() {
    private class IReaderNovelTable : IntIdTable() {
        val url = varchar("url", 2048)
        val title = varchar("title", 512)
        val initialized = bool("initialized").default(false)

        val artist = text("artist").nullable()
        val author = text("author").nullable()
        val description = text("description").nullable()
        val genre = text("genre").nullable()

        val status = long("status").default(0)
        val thumbnailUrl = varchar("thumbnail_url", 2048).nullable()

        val inLibrary = bool("in_library").default(false)
        val inLibraryAt = long("in_library_at").default(0)

        val sourceReference = long("source")

        val lastFetchedAt = long("last_fetched_at").default(0)
        val chaptersLastFetchedAt = long("chapters_last_fetched_at").default(0)
    }

    override val tables: Array<Table>
        get() = arrayOf(IReaderNovelTable())
}
