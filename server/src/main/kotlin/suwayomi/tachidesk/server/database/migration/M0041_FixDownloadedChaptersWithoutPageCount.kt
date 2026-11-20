package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0041_FixDownloadedChaptersWithoutPageCount : SQLMigration() {
    override val sql: String =
        """
        UPDATE CHAPTER
        SET IS_DOWNLOADED = FALSE
        WHERE IS_DOWNLOADED = TRUE AND PAGE_COUNT <= 0
        """.trimIndent()
}
