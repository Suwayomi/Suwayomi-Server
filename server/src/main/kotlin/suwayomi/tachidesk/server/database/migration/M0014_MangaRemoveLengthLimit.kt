package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0014_MangaRemoveLengthLimit : SQLMigration() {
    override val sql =
        """
        ALTER TABLE MANGA ALTER COLUMN ARTIST VARCHAR(512);
        ALTER TABLE MANGA ALTER COLUMN AUTHOR VARCHAR(512);
        ALTER TABLE MANGA ALTER COLUMN DESCRIPTION VARCHAR; -- the default length is `Integer.MAX_VALUE`
        ALTER TABLE MANGA ALTER COLUMN GENRE VARCHAR; -- the default length is `Integer.MAX_VALUE`
        """.trimIndent()
}
