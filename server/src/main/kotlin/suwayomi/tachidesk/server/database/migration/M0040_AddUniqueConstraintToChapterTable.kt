package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0040_AddUniqueConstraintToChapterTable : SQLMigration() {
    override val sql: String =
        """
        ALTER TABLE CHAPTER
            ADD CONSTRAINT UC_CHAPTER UNIQUE (URL, MANGA)
        """.trimIndent()
}
