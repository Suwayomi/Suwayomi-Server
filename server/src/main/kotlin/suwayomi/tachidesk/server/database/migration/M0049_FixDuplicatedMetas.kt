package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0049_FixDuplicatedMetas : SQLMigration() {
    private fun createMigrationForTable(
        table: String,
        refColumn: String? = null,
    ): String {
        val groupBy = listOfNotNull(refColumn, "KEY").joinToString(", ")

        return """
            DELETE FROM $table
            WHERE ID NOT IN (
                SELECT MIN(ID)
                FROM $table
                GROUP BY $groupBy
            );

            ALTER TABLE $table
                ADD CONSTRAINT UC_$table UNIQUE ($groupBy);
            """.trimIndent()
    }

    override val sql: String =
        createMigrationForTable("CATEGORYMETA", "CATEGORY_REF") +
            createMigrationForTable("CHAPTERMETA", "CHAPTER_REF") +
            createMigrationForTable("GLOBALMETA") +
            createMigrationForTable("MANGAMETA", "MANGA_REF") +
            createMigrationForTable("SOURCEMETA", "SOURCE_REF")
}
