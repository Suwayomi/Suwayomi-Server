package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0050_FixHandlingOfTooLongPageImageUrls : SQLMigration() {
    override val sql: String =
        """
        ALTER TABLE PAGE DROP CONSTRAINT UC_PAGE;
        ALTER TABLE PAGE ADD CONSTRAINT UC_PAGE UNIQUE (INDEX, CHAPTER);
        
        ALTER TABLE PAGE ALTER COLUMN IMAGE_URL VARCHAR; -- the default length is `Integer.MAX_VALUE`
        """.trimIndent()
}
