package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0060_AddChapterIndexes : SQLMigration() {
    override val sql =
        """
        CREATE INDEX IF NOT EXISTS chapter_manga_source_order ON CHAPTER (manga, source_order);
        CREATE INDEX IF NOT EXISTS chapter_manga_read_source_order ON CHAPTER (manga, READ, source_order);
        CREATE INDEX IF NOT EXISTS chapter_manga_last_read_at ON CHAPTER (manga, last_read_at DESC);
        CREATE INDEX IF NOT EXISTS chapter_manga_fetched_at ON CHAPTER (manga, fetched_at DESC, source_order DESC);
        CREATE INDEX IF NOT EXISTS chapter_manga_date_upload ON CHAPTER (manga, date_upload DESC, source_order DESC);
        CREATE INDEX IF NOT EXISTS page_chapter ON PAGE (chapter);
        """.trimIndent()
}
