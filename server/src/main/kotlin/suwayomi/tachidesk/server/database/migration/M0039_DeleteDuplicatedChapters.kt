package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0039_DeleteDuplicatedChapters : SQLMigration() {
    override val sql: String =
        """
        DELETE FROM CHAPTER
        WHERE id NOT IN (
            SELECT MIN(id)
            FROM CHAPTER
            GROUP BY URL, MANGA
        );
        """.trimIndent()
}
