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
class M0035_TrackSearch : AddTableMigration() {
    private class TrackSearchTable : IntIdTable() {
        val trackerId = integer("tracker_id")
        val remoteId = long("remote_id")
        val title = varchar("title", 512)
        val totalChapters = integer("total_chapters")
        val trackingUrl = varchar("tracking_url", 512)
        val coverUrl = varchar("cover_url", 512)
        val summary = varchar("summary", 4096)
        val publishingStatus = varchar("publishing_status", 512)
        val publishingType = varchar("publishing_type", 512)
        val startDate = varchar("start_date", 128)
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                TrackSearchTable(),
            )
}
