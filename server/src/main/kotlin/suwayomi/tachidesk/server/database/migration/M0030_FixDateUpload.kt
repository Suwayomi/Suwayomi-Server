package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0030_FixDateUpload : SQLMigration() {
    // language=h2
    override val sql =
        """
        UPDATE CHAPTER 
        SET DATE_UPLOAD = (FETCHED_AT * 1000)
        WHERE DATE_UPLOAD = 0;
        """.trimIndent()
}
