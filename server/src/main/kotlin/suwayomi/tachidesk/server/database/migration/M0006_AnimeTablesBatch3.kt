package suwayomi.tachidesk.server.database.migration

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.server.database.migration.lib.Migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

class M0006_AnimeTablesBatch3 : Migration() {
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

        val anime = reference("anime", AnimeTable)
    }

    override fun run() {
        transaction {
            SchemaUtils.create(
                EpisodeTable()
            )
        }
    }
}
