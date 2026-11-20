package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.server.database.migration.helpers.MAYBE_TYPE_PREFIX
import suwayomi.tachidesk.server.database.migration.helpers.UNLIMITED_TEXT

@Suppress("ClassName", "unused")
class M0050_FixHandlingOfTooLongPageImageUrls : SQLMigration() {
    override val sql: String =
        """
        DELETE FROM PAGE
        WHERE ID NOT IN (
            SELECT MIN(ID)
            FROM PAGE
            GROUP BY INDEX, CHAPTER
        );
            
        ALTER TABLE PAGE DROP CONSTRAINT IF EXISTS UC_PAGE;
        ALTER TABLE PAGE ADD CONSTRAINT UC_PAGE UNIQUE (INDEX, CHAPTER);
        
        ALTER TABLE PAGE ALTER COLUMN IMAGE_URL $MAYBE_TYPE_PREFIX$UNLIMITED_TEXT; -- the default length is `Integer.MAX_VALUE`
        """.trimIndent()
}
