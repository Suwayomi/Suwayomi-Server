package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0043_PreventNegativeLastPageRead : SQLMigration() {
    override val sql: String =
        """
        UPDATE CHAPTER
        SET LAST_PAGE_READ = 0
        WHERE LAST_PAGE_READ < 0;

        ALTER TABLE CHAPTER
            ADD CONSTRAINT CHK_LAST_READ_PAGE_POSITIVE CHECK (LAST_PAGE_READ >= 0)
        """.trimIndent()
}
