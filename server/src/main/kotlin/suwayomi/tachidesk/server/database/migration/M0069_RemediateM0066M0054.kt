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
 * Remediation for known historical-migration defects. Historical migrations must not be
 * edited, so all fixes land here; every statement is a no-op on a healthy database.
 *
 * 1. M0066 added `UC_TRACKRECORD_UNIQUE (USER_ID, MANGA_ID, SYNC_ID)` without deduplicating
 *    TRACKRECORD first — TRACKSEARCH and SOURCE got a dedupe in the same migration (commit
 *    413336ac), TRACKRECORD was missed. Duplicates can exist from the pre-M0066 era (no
 *    unique constraint before it), non-atomic select-then-insert upserts, or backup imports.
 *    The DELETE below is a no-op where the constraint was added cleanly.
 *
 *    LIMITATION: if M0066's ADD CONSTRAINT itself already failed on existing duplicates, the
 *    installation can never start far enough to reach M0069 — the only remedy is running
 *    the same DELETE manually and restarting.
 *
 * 2. M0066's PostgreSQL branch inserted the admin user with an explicit `ID = 1` without
 *    advancing the serial sequence, so the first `insertAndGetId` drew nextval 1 and failed
 *    on the PK (a retry self-heals, but the first registration attempt visibly fails). The
 *    setval below pushes the sequence past `MAX(id)`. H2 has no `pg_get_serial_sequence`
 *    and its identity columns never had this problem (the H2 branch of M0066 inserts
 *    without an explicit ID).
 *
 * 3. M0066+M0067 never added a `(user_id, manga)` index on MANGAUSER, which the sync pull
 *    path scans per user.
 *
 * 4. M0023 left `CATEGORYMETA.category_ref INT DEFAULT 0` behind; the model declares a plain
 *    reference with no default, so the stale default is dropped (model drift cleanup).
 *
 * Known historical limitations that cannot be retro-fixed (documented, not forgotten):
 * - M0054 moves PostgreSQL tables into the literal `suwayomi` schema, ignoring
 *   `serverConfig.databaseSchema`. An install with a custom schema must keep the default
 *   schema until the migration chain has passed M0054, or fix the schema manually — M0069
 *   cannot move the tables back because M0054 only runs on a PostgreSQL first migration.
 * - M0066's `columnExists` does not filter `TABLE_SCHEMA`; the SOURCE unique constraint is
 *   redundant; M0053/M0060/M0049/M0057 low-severity issues and M0066's authUsername
 *   take(64)/H2 explicit ID=1 remain as known limitations.
 * - `UC_CHAPTER`/`UC_PAGE`/`uc_pkg_name` are DB-only constraints and intentionally not
 *   reflected back into the Exposed table models.
 */
@Suppress("ClassName", "unused")
class M0069_RemediateM0066M0054 : SQLMigration() {
    override val sql by lazy {
        when (serverConfig.databaseType.value) {
            DatabaseType.H2 -> h2Sql()
            DatabaseType.POSTGRESQL -> postgresSql()
        }
    }

    private fun h2Sql(): String {
        val trackRecordTable = "TRACKRECORD".toSqlName()
        val mangaUserTable = "MANGAUSER".toSqlName()
        val categoryMetaTable = "CATEGORYMETA".toSqlName()

        @Language("H2")
        return """
            -- 1. TRACKRECORD dedupe, keeping the first row per (user, manga, tracker)
            DELETE FROM $trackRecordTable WHERE ID NOT IN (
                SELECT MIN(ID) FROM $trackRecordTable GROUP BY USER_ID, MANGA_ID, SYNC_ID
            );

            -- 2. PostgreSQL only, see class doc

            -- 3. Missing MANGAUSER (user_id, manga) index for the sync pull path
            CREATE INDEX IF NOT EXISTS mangauser_user_manga ON $mangaUserTable (user_id, manga);

            -- 4. Stale M0023 default on CATEGORYMETA.category_ref
            ALTER TABLE $categoryMetaTable ALTER COLUMN category_ref DROP DEFAULT;
            """.trimIndent()
    }

    private fun postgresSql(): String {
        // Migrations must qualify the configured schema explicitly: the connection's
        // search_path does not reliably include it.
        val schema = serverConfig.databaseSchema.value
        val trackRecordTable = "\"$schema\".${"TRACKRECORD".toSqlName()}"
        val userAccountTable = "\"$schema\".${"USERACCOUNT".toSqlName()}"
        val mangaUserTable = "\"$schema\".${"MANGAUSER".toSqlName()}"
        val categoryMetaTable = "\"$schema\".${"CATEGORYMETA".toSqlName()}"

        @Language("PostgreSQL")
        return """
            -- 1. TRACKRECORD dedupe, keeping the first row per (user, manga, tracker)
            DELETE FROM $trackRecordTable WHERE id NOT IN (
                SELECT MIN(id) FROM $trackRecordTable GROUP BY user_id, manga_id, sync_id
            );

            -- 2. Advance the useraccount serial past existing ids (M0066 seeded id=1 explicitly)
            DO $$
            DECLARE seq text;
            BEGIN
                seq := pg_get_serial_sequence('$schema.useraccount', 'id');
                IF seq IS NOT NULL THEN
                    PERFORM setval(seq, GREATEST((SELECT COALESCE(MAX(id), 0) FROM $userAccountTable), 1));
                END IF;
            END $$;

            -- 3. Missing MANGAUSER (user_id, manga) index for the sync pull path
            CREATE INDEX IF NOT EXISTS mangauser_user_manga ON $mangaUserTable (user_id, manga);

            -- 4. Stale M0023 default on CATEGORYMETA.category_ref
            ALTER TABLE $categoryMetaTable ALTER COLUMN category_ref DROP DEFAULT;
            """.trimIndent()
    }
}
