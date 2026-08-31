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

// Chapters are versioned and merged separately in sync protocol v2; bumping the manga version per
// chapter read let the device that read more chapters win all manga-level merges (categories,
// tracking, favorite) and silently revert the other device's changes.
@Suppress("ClassName", "unused")
class M0065_ChapterVersionOnlyBumpsChapter : SQLMigration() {
    override val sql =
        when (serverConfig.databaseType.value) {
            // the H2 trigger body is Kotlin (UpdateChapterAndMangaVersionTrigger)
            DatabaseType.H2 -> {
                "SELECT 1;"
            }

            DatabaseType.POSTGRESQL -> {
                // language=postgresql
                """
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
                """.trimIndent()
            }
        }
}
