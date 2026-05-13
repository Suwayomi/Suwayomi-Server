package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.database.migration.helpers.toSqlName
import suwayomi.tachidesk.server.serverConfig

@Suppress("ClassName", "unused")
class M0055_RenameMetaKeys : SQLMigration() {
    fun postgresRename(table: String): String =
        "ALTER TABLE $table " +
            "RENAME COLUMN " + "KEY".toSqlName() + " TO META_KEY;"

    fun h2Rename(table: String): String =
        "ALTER TABLE $table " +
            "ALTER COLUMN " + "KEY".toSqlName() + " RENAME TO META_KEY;"

    fun createRenameMigration(table: String): String =
        when (serverConfig.databaseType.value) {
            DatabaseType.H2 -> h2Rename(table.toSqlName())
            DatabaseType.POSTGRESQL -> postgresRename(table.toSqlName())
        }

    override val sql: String by lazy {
        createRenameMigration("CATEGORYMETA") +
            createRenameMigration("CHAPTERMETA") +
            createRenameMigration("GLOBALMETA") +
            createRenameMigration("MANGAMETA") +
            createRenameMigration("SOURCEMETA")
    }
}
