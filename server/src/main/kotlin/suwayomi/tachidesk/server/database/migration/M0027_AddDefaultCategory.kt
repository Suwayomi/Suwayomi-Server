package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

@Suppress("ClassName", "unused")
class M0027_AddDefaultCategory : SQLMigration() {
    override val sql: String = """
        INSERT INTO CATEGORY (ID, NAME, IS_DEFAULT, "ORDER", INCLUDE_IN_UPDATE) VALUES (0, 'Default', TRUE, 0, -1)
    """.trimIndent()
}
