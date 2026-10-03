package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

// The updates list sorts every library chapter by fetched_at DESC, source_order DESC, id ASC.
// Without an index in that order the database reads and sorts the whole chapter table for
// each page and for each pagination bound; with it, it walks the index and stops at the limit.
@Suppress("ClassName", "unused")
class M0067_AddChapterFetchedAtIndex : SQLMigration() {
    // language=sql
    override val sql: String =
        """
        CREATE INDEX IF NOT EXISTS chapter_fetched_at ON chapter (fetched_at DESC, source_order DESC, id);
        """.trimIndent()
}
