package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.serverConfig

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/**
 * Moves existing PostgreSQL tables from the `public` schema to the `suwayomi` schema.
 * This is needed for users who had tables created in the public schema before the HikariCP fix.
 *
 * - Fresh PostgreSQL: No-op (tables already in suwayomi)
 * - Existing PostgreSQL: Moves tables from public to suwayomi
 * - H2: No-op (empty string)
 */
@Suppress("ClassName", "unused")
class M0054_MovePostgresToSuwayomiSchema : SQLMigration() {
    override val sql by lazy {
        when (serverConfig.databaseType.value) {
            DatabaseType.H2 -> h2SchemaMigration()
            DatabaseType.POSTGRESQL -> postgresSchemaMigration()
        }
    }

    fun h2SchemaMigration(): String = "-- H2 does not need schema migration"

    fun postgresSchemaMigration(): String =
        """
        DO $$
        DECLARE
            r RECORD;
        BEGIN
            -- Check if manga table exists in public schema (indicates data needs migration)
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'manga'
            ) THEN
                RAISE NOTICE 'No Suwayomi tables found in public schema, skipping migration';
                RETURN;
            END IF;

            RAISE NOTICE 'Detected Suwayomi tables in public schema, moving to suwayomi schema';

            -- Drop empty suwayomi tables and move public tables over
            FOR r IN
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            LOOP
                -- Drop the empty suwayomi table if it exists
                IF EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'suwayomi' AND table_name = r.table_name
                ) THEN
                    EXECUTE format('DROP TABLE suwayomi.%I CASCADE', r.table_name);
                END IF;

                -- Move the public table to suwayomi schema
                EXECUTE format('ALTER TABLE public.%I SET SCHEMA suwayomi', r.table_name);
                RAISE NOTICE 'Moved table % to suwayomi schema', r.table_name;
            END LOOP;
        END $$
        """.trimIndent()
}
