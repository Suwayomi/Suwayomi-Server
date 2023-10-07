package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0020_AddMangaLastFetchedAtColumns : SQLMigration() {
    override val sql =
        """
        ALTER TABLE Manga ADD COLUMN last_fetched_at BIGINT DEFAULT 0;
        ALTER TABLE Manga ADD COLUMN chapters_last_fetched_at BIGINT DEFAULT 0;
        """.trimIndent()
}
