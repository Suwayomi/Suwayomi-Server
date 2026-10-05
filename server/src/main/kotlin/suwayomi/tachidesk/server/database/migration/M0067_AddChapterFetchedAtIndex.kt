package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

// Lets the updates list walk the index instead of sorting every chapter
@Suppress("ClassName", "unused")
class M0067_AddChapterFetchedAtIndex : SQLMigration() {
    // language=sql
    override val sql: String =
        """
        CREATE INDEX IF NOT EXISTS chapter_fetched_at ON chapter (fetched_at DESC, source_order DESC, id);
        """.trimIndent()
}
