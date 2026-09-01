package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.server.database.migration.helpers.toSqlName

@Suppress("ClassName", "unused")
class M0063_FixExtensionsIncorrectMarkedUpdatable : SQLMigration() {
    override val sql: String by lazy {
        val extension = "extension".toSqlName()
        val hasUpdate = "has_update".toSqlName()

        """
        UPDATE $extension SET $hasUpdate = TRUE WHERE has_update = FALSE
        """.trimIndent()
    }
}
