package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

/**
 * The binary hash belongs to the downloaded file, shared by every user. The
 * per-user values are dropped, as some came from the file name checksum, and
 * are generated again when needed.
 */
@Suppress("ClassName", "unused")
class M0068_MoveKoreaderHashToChapter : SQLMigration() {
    override val sql =
        """
        ALTER TABLE CHAPTER ADD COLUMN IF NOT EXISTS KOREADER_HASH VARCHAR(32);
        ALTER TABLE CHAPTERUSER DROP COLUMN IF EXISTS KOREADER_HASH;
        """.trimIndent()
}
