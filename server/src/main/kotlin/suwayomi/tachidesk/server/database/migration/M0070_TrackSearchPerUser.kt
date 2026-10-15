package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration
import org.intellij.lang.annotations.Language
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.database.migration.helpers.toSqlName
import suwayomi.tachidesk.server.serverConfig

/**
 * U3 data-integrity migration.
 *
 * 1. TRACKSEARCH gains a USER_ID column and the logical key becomes
 *    (TRACKER_ID, REMOTE_ID, USER_ID): the cached rows carry per-user tracker state
 *    (LIBRARY_ID, score, reading progress, the PRIVATE flag), so the old global
 *    (TRACKER_ID, REMOTE_ID) key let one user's search result leak into — and overwrite —
 *    another user's. Legacy rows were a shared cache and cannot be attributed to a
 *    single user; they are wiped rather than backfilled (the column is added with
 *    DEFAULT 0 only to satisfy NOT NULL on populated tables, then the default is dropped
 *    again so the schema matches the model).
 *
 * 2. Ordinary (source, url) on MANGA and (manga, url) on CHAPTER lookup indexes.
 *    These are deliberately NON-unique: duplicate (source, url) manga rows and
 *    (manga, url) chapter rows can legitimately exist from historical data, backup
 *    restores and concurrent source updates, so a unique constraint would be a
 *    correctness hazard — deduplication stays an application-layer invariant.
 */
@Suppress("ClassName", "unused")
class M0070_TrackSearchPerUser : SQLMigration() {
    override val sql by lazy {
        when (serverConfig.databaseType.value) {
            DatabaseType.H2 -> h2Sql()
            DatabaseType.POSTGRESQL -> postgresSql()
        }
    }

    private fun h2Sql(): String {
        val trackSearchTable = "TRACKSEARCH".toSqlName()
        val userAccountTable = "USERACCOUNT".toSqlName()
        val mangaTable = "MANGA".toSqlName()
        val chapterTable = "CHAPTER".toSqlName()

        @Language("H2")
        return """
            -- 1a. per-user track search key
            ALTER TABLE $trackSearchTable ADD COLUMN IF NOT EXISTS USER_ID INT NOT NULL DEFAULT 0;
            -- legacy rows are a shared cache with user-private fields: clear, don't backfill
            DELETE FROM $trackSearchTable;
            ALTER TABLE $trackSearchTable DROP CONSTRAINT IF EXISTS UC_TRACKSEARCH_TRACKER_ID_REMOTE_ID;
            ALTER TABLE $trackSearchTable DROP CONSTRAINT IF EXISTS UC_TRACKSEARCH_TRACKER_ID_REMOTE_ID_USER_ID;
            ALTER TABLE $trackSearchTable ADD CONSTRAINT UC_TRACKSEARCH_TRACKER_ID_REMOTE_ID_USER_ID UNIQUE (TRACKER_ID, REMOTE_ID, USER_ID);
            ALTER TABLE $trackSearchTable DROP CONSTRAINT IF EXISTS FK_TRACKSEARCH_USER_ID;
            ALTER TABLE $trackSearchTable ADD CONSTRAINT FK_TRACKSEARCH_USER_ID FOREIGN KEY (USER_ID) REFERENCES $userAccountTable(ID) ON DELETE CASCADE;
            ALTER TABLE $trackSearchTable ALTER COLUMN USER_ID DROP DEFAULT;

            -- 2. ordinary lookup indexes (non-unique on purpose, see class doc)
            CREATE INDEX IF NOT EXISTS manga_source_url ON $mangaTable (source, url);
            CREATE INDEX IF NOT EXISTS chapter_manga_url ON $chapterTable (manga, url);
            """.trimIndent()
    }

    private fun postgresSql(): String {
        // Migrations must qualify the configured schema explicitly: the connection's
        // search_path does not reliably include it.
        val schema = serverConfig.databaseSchema.value
        val trackSearchTable = "\"$schema\".${"TRACKSEARCH".toSqlName()}"
        val userAccountTable = "\"$schema\".${"USERACCOUNT".toSqlName()}"
        val mangaTable = "\"$schema\".${"MANGA".toSqlName()}"
        val chapterTable = "\"$schema\".${"CHAPTER".toSqlName()}"

        @Language("PostgreSQL")
        return """
            -- 1a. per-user track search key
            ALTER TABLE $trackSearchTable ADD COLUMN IF NOT EXISTS user_id INT NOT NULL DEFAULT 0;
            -- legacy rows are a shared cache with user-private fields: clear, don't backfill
            DELETE FROM $trackSearchTable;
            ALTER TABLE $trackSearchTable DROP CONSTRAINT IF EXISTS uc_tracksearch_tracker_id_remote_id;
            ALTER TABLE $trackSearchTable DROP CONSTRAINT IF EXISTS uc_tracksearch_tracker_id_remote_id_user_id;
            ALTER TABLE $trackSearchTable ADD CONSTRAINT uc_tracksearch_tracker_id_remote_id_user_id UNIQUE (tracker_id, remote_id, user_id);
            ALTER TABLE $trackSearchTable DROP CONSTRAINT IF EXISTS fk_tracksearch_user_id;
            ALTER TABLE $trackSearchTable ADD CONSTRAINT fk_tracksearch_user_id FOREIGN KEY (user_id) REFERENCES $userAccountTable(id) ON DELETE CASCADE;
            ALTER TABLE $trackSearchTable ALTER COLUMN user_id DROP DEFAULT;

            -- 2. ordinary lookup indexes (non-unique on purpose, see class doc)
            CREATE INDEX IF NOT EXISTS manga_source_url ON $mangaTable (source, url);
            CREATE INDEX IF NOT EXISTS chapter_manga_url ON $chapterTable (manga, url);
            """.trimIndent()
    }
}
