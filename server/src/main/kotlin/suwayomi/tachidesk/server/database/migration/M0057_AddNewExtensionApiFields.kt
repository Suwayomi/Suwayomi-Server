package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.manga.model.dataclass.ContentWarning
import suwayomi.tachidesk.server.database.migration.helpers.MAYBE_TYPE_PREFIX
import suwayomi.tachidesk.server.database.migration.helpers.UNLIMITED_TEXT
import suwayomi.tachidesk.server.database.migration.helpers.toSqlName
import suwayomi.tachidesk.server.serverConfig

@Suppress("ClassName", "unused")
class M0057_AddNewExtensionApiFields : SQLMigration() {
    fun postgresRename(): String =
        "ALTER TABLE EXTENSION RENAME COLUMN " + "repo".toSqlName() + " TO " + "store_index_url".toSqlName() + ";"

    fun h2Rename(): String =
        "ALTER TABLE EXTENSION ALTER COLUMN " + "repo".toSqlName() + " RENAME TO " + "store_index_url".toSqlName() + ";"

    override val sql by lazy {
        """
        ALTER TABLE manga ADD COLUMN memo $UNLIMITED_TEXT DEFAULT '{}' NOT NULL;
        ALTER TABLE chapter ADD COLUMN memo $UNLIMITED_TEXT DEFAULT '{}' NOT NULL;
        ${
            when (serverConfig.databaseType.value) {
                DatabaseType.POSTGRESQL -> postgresRename()
                DatabaseType.H2 -> h2Rename()
            }
        }
        ALTER TABLE EXTENSION ALTER COLUMN store_index_url ${MAYBE_TYPE_PREFIX}VARCHAR(2048);
        ALTER TABLE EXTENSION ALTER COLUMN version_code ${MAYBE_TYPE_PREFIX}BIGINT;
        ALTER TABLE EXTENSION ALTER COLUMN apk_name DROP NOT NULL;
        ${
            when (serverConfig.databaseType.value) {
                DatabaseType.POSTGRESQL -> postgresBackfill()
                DatabaseType.H2 -> h2Backfill()
            }
        }
        ALTER TABLE EXTENSION ADD COLUMN apk_url VARCHAR(2048);
        ALTER TABLE EXTENSION ADD COLUMN content_warning INTEGER DEFAULT 0;
        UPDATE EXTENSION SET content_warning = ${ContentWarning.MIXED.ordinal} WHERE is_nsfw = TRUE;
        ALTER TABLE EXTENSION DROP COLUMN is_nsfw;
        ALTER TABLE SOURCE ADD COLUMN content_warning INTEGER DEFAULT 0;
        UPDATE SOURCE SET content_warning = ${ContentWarning.MIXED.ordinal} WHERE is_nsfw = TRUE;
        ALTER TABLE SOURCE DROP COLUMN is_nsfw;
        

        """.trimIndent()
    }

    fun postgresBackfill() =
        """
        -- 1. Add the column as nullable to avoid table locks
        ALTER TABLE EXTENSION ADD COLUMN extension_lib VARCHAR(16);
        -- 2. Backfill existing rows using the first two parts of the version_name (split by dot)
        UPDATE EXTENSION
        SET extension_lib = CONCAT(
            SPLIT_PART(version_name, '.', 1), 
            '.', 
            SPLIT_PART(version_name, '.', 2)
        );
        -- 3. Enforce the NOT NULL constraint 
        ALTER TABLE EXTENSION ALTER COLUMN extension_lib SET NOT NULL;
        """.trimIndent()

    fun h2Backfill() =
        """
        -- 1. Add the column as nullable
        ALTER TABLE EXTENSION ADD COLUMN extension_lib VARCHAR(16);
        -- 2. Backfill rows by extracting text up to the second dot
        UPDATE EXTENSION
        SET extension_lib = CASE 
            -- If there's a second dot (e.g. 1.2.3), grab everything before it
            WHEN LOCATE('.', version_name, LOCATE('.', version_name) + 1) > 0 
            THEN SUBSTRING(version_name, 1, LOCATE('.', version_name, LOCATE('.', version_name) + 1) - 1)
            -- If there's no second dot (e.g. 1.2), keep the original value
            ELSE version_name 
        END;
        -- 3. Enforce the NOT NULL constraint
        ALTER TABLE EXTENSION ALTER COLUMN extension_lib SET NOT NULL;
        """.trimIndent()
}
