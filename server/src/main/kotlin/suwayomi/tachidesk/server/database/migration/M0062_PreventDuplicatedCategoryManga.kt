package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.server.database.migration.helpers.toSqlName

@Suppress("ClassName", "unused")
class M0062_PreventDuplicatedCategoryManga : SQLMigration() {
    override val sql: String by lazy {
        val table = "CATEGORYMANGA".toSqlName()
        val manga = "MANGA".toSqlName()
        val category = "CATEGORY".toSqlName()

        """
        DELETE FROM $table
        WHERE ID NOT IN (
            SELECT MIN(ID)
            FROM $table
            GROUP BY $manga, $category
        );

        ALTER TABLE $table
            ADD CONSTRAINT UC_${"CATEGORYMANGA"} UNIQUE ($manga, $category);
        """.trimIndent()
    }
}
