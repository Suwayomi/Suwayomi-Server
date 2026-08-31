package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

// Tachiyomi sort/display/reader bitmasks, kept opaque so a sync merge won by Suwayomi
// does not zero them on other clients.
@Suppress("ClassName", "unused")
class M0064_AddSyncFlags : SQLMigration() {
    // language=sql
    override val sql: String =
        """
        ALTER TABLE category ADD COLUMN flags INT NOT NULL DEFAULT 0;
        ALTER TABLE manga ADD COLUMN viewer INT NOT NULL DEFAULT 0;
        ALTER TABLE manga ADD COLUMN viewer_flags INT NULL;
        ALTER TABLE manga ADD COLUMN chapter_flags INT NOT NULL DEFAULT 0;
        """.trimIndent()
}
