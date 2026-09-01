package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.serverConfig

// On PostgreSQL the M0056 version triggers were AFTER UPDATE and their NEW.version assignment was
// discarded; last_modified_at was stamped on sync restores too; category removals and track
// records did not bump the manga version.
@Suppress("ClassName", "unused")
class M0063_FixSyncYomiTriggers : SQLMigration() {
    override val sql =
        when (serverConfig.databaseType.value) {
            DatabaseType.POSTGRESQL -> postgresQuery()
            DatabaseType.H2 -> h2Query()
        }

    // language=postgresql
    fun postgresQuery(): String =
        """
        DROP TRIGGER IF EXISTS update_manga_version ON manga;
        CREATE TRIGGER update_manga_version
        BEFORE UPDATE ON manga
        FOR EACH ROW
        EXECUTE FUNCTION update_manga_version();

        -- chapters merge separately by their own version; the per-read manga bump only let the
        -- device that read more chapters win manga-level merges
        CREATE OR REPLACE FUNCTION update_chapter_and_manga_version()
        RETURNS trigger AS $$
        BEGIN
            IF NOT NEW.is_syncing
               AND ROW(NEW.read, NEW.bookmark, NEW.last_page_read)
                   IS DISTINCT FROM
                   ROW(OLD.read, OLD.bookmark, OLD.last_page_read)
            THEN
                NEW.version := OLD.version + 1;
            END IF;

            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        DROP TRIGGER IF EXISTS update_chapter_and_manga_version ON chapter;
        CREATE TRIGGER update_chapter_and_manga_version
        BEFORE UPDATE ON chapter
        FOR EACH ROW
        EXECUTE FUNCTION update_chapter_and_manga_version();


        -- BEFORE triggers fire alphabetically: this runs before update_manga_version, so the edited
        -- columns must be listed here, not just "version".
        CREATE OR REPLACE FUNCTION update_manga_last_modified_at()
        RETURNS trigger AS $$
        BEGIN
            IF NEW.is_syncing THEN
                RETURN NEW;
            END IF;

            IF TG_OP = 'UPDATE'
               AND ROW(NEW.url, NEW.description, NEW.in_library, NEW.version)
                   IS NOT DISTINCT FROM
                   ROW(OLD.url, OLD.description, OLD.in_library, OLD.version)
            THEN
                RETURN NEW;
            END IF;

            NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        CREATE OR REPLACE FUNCTION update_chapter_last_modified_at()
        RETURNS trigger AS $$
        BEGIN
            IF NEW.is_syncing THEN
                RETURN NEW;
            END IF;

            IF TG_OP = 'UPDATE'
               AND ROW(NEW.read, NEW.bookmark, NEW.last_page_read, NEW.version)
                   IS NOT DISTINCT FROM
                   ROW(OLD.read, OLD.bookmark, OLD.last_page_read, OLD.version)
            THEN
                RETURN NEW;
            END IF;

            NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;


        CREATE OR REPLACE FUNCTION delete_manga_category_update_version()
        RETURNS trigger AS $$
        BEGIN
            UPDATE manga SET version = version + 1 WHERE id = OLD.manga AND is_syncing = FALSE;

            RETURN OLD;
        END;
        $$ LANGUAGE plpgsql;

        DROP TRIGGER IF EXISTS delete_manga_category_update_version ON categorymanga;
        CREATE TRIGGER delete_manga_category_update_version
        AFTER DELETE ON categorymanga
        FOR EACH ROW
        EXECUTE FUNCTION delete_manga_category_update_version();


        CREATE OR REPLACE FUNCTION trackrecord_update_manga_version()
        RETURNS trigger AS $$
        BEGIN
            UPDATE manga SET version = version + 1
            WHERE id = COALESCE(NEW.manga_id, OLD.manga_id) AND is_syncing = FALSE;

            RETURN COALESCE(NEW, OLD);
        END;
        $$ LANGUAGE plpgsql;

        DROP TRIGGER IF EXISTS trackrecord_update_manga_version ON trackrecord;
        CREATE TRIGGER trackrecord_update_manga_version
        AFTER INSERT OR UPDATE OR DELETE ON trackrecord
        FOR EACH ROW
        EXECUTE FUNCTION trackrecord_update_manga_version();


        -- random(min, max) only exists on PostgreSQL 17+
        CREATE OR REPLACE FUNCTION insert_category_uid()
        RETURNS trigger AS $$
        BEGIN
            IF NEW.uid = 0 THEN
                NEW.uid := (floor(random() * 9223372036854775806) + 1)::bigint;
            END IF;

            IF NEW.last_modified_at = 0 THEN
                NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
            END IF;

            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
        """.trimIndent()

    // language=h2
    fun h2Query(): String =
        """
        CREATE TRIGGER IF NOT EXISTS delete_manga_category_update_version
        AFTER DELETE ON categorymanga
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.DeleteMangaCategoryUpdateVersionTrigger";

        CREATE TRIGGER IF NOT EXISTS trackrecord_update_manga_version
        AFTER INSERT, UPDATE, DELETE ON trackrecord
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.TrackRecordUpdateMangaVersionTrigger";
        """.trimIndent()
}
