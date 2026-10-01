package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0067_AddChapterIndexes : SQLMigration() {
    override val sql =
        """
        CREATE INDEX IF NOT EXISTS chapter_manga_source_order ON CHAPTER (manga, source_order);
        CREATE INDEX IF NOT EXISTS chapter_manga_fetched_at ON CHAPTER (manga, fetched_at DESC, source_order DESC);
        CREATE INDEX IF NOT EXISTS chapter_manga_date_upload ON CHAPTER (manga, date_upload DESC, source_order DESC);
        CREATE INDEX IF NOT EXISTS chapteruser_user_read_chapter ON CHAPTERUSER (user_id, READ, chapter);
        CREATE INDEX IF NOT EXISTS chapteruser_user_last_read_at_chapter ON CHAPTERUSER (user_id, last_read_at DESC, chapter);
        CREATE INDEX IF NOT EXISTS chapteruser_user_downloaded_chapter ON CHAPTERUSER (user_id, is_downloaded, chapter);
        CREATE INDEX IF NOT EXISTS chapteruser_user_bookmarked_chapter ON CHAPTERUSER (user_id, bookmark, chapter);
        """.trimIndent()
}
