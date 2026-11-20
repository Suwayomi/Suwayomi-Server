package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

// New chapters without an upload date fell back to the fetch time in seconds instead of
// milliseconds. 10^11 ms is March 1973 while 10^11 s is the year 5138, so every value in
// that range is a seconds timestamp. date_upload is not a synced column, so the SyncYomi
// version and last_modified_at triggers don't fire.
@Suppress("ClassName", "unused")
class M0065_FixSecondsFallbackUploadDate : SQLMigration() {
    // language=sql
    override val sql: String =
        """
        UPDATE chapter
        SET date_upload = date_upload * 1000
        WHERE date_upload > 0 AND date_upload < 100000000000;
        """.trimIndent()
}
