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
class M0006_AnimeTablesBatch3 : AddTableMigration() {
    // dummy table
    private class AnimeTable : IntIdTable()

    private class EpisodeTable : IntIdTable() {
        val url = varchar("url", 2048)
        val name = varchar("name", 512)
        val date_upload = long("date_upload").default(0)
        val episode_number = float("episode_number").default(-1f)
        val scanlator = varchar("scanlator", 128).nullable()

        val isRead = bool("read").default(false)
        val isBookmarked = bool("bookmark").default(false)
        val lastPageRead = integer("last_page_read").default(0)

        // index is reserved by a function
        val animeIndex = integer("index")

        val anime = reference("anime", AnimeTable())
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                EpisodeTable(),
            )
}
