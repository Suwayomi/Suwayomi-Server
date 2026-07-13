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
class M0060_MakeExtensionPkgNameUnique : SQLMigration() {
    override val sql by lazy {
        """
        ALTER TABLE ${"extension".toSqlName()}
        ADD CONSTRAINT uc_pkg_name
        UNIQUE (${"pkg_name".toSqlName()});
        """.trimIndent()
    }
}
