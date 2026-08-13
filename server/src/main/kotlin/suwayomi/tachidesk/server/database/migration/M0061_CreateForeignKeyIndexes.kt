package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0061_CreateForeignKeyIndexes : SQLMigration() {
    override val sql by lazy {
        """
        CREATE INDEX idx_categorymanga_category ON CATEGORYMANGA (category);
        CREATE INDEX idx_categorymanga_manga ON CATEGORYMANGA (manga);
        
        CREATE INDEX idx_categorymeta_category ON CATEGORYMETA (category_ref);
        
        CREATE INDEX idx_chapter_manga ON CHAPTER (manga);
        CREATE INDEX idx_chaptermeta_chapter ON CHAPTERMETA (chapter_ref);
        
        CREATE INDEX idx_mangameta_manga ON MANGAMETA (manga_ref);
        
        CREATE INDEX idx_page_chapter ON PAGE (chapter);
        
        CREATE INDEX idx_source_extension ON SOURCE (extension);
        CREATE INDEX idx_sourcemeta_source ON SOURCEMETA (source_ref);
        
        CREATE INDEX idx_trackrecord_manga ON TRACKRECORD (manga_id);
        """.trimIndent()
    }
}
