package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table

@Suppress("ClassName", "unused")
class M0036_SourceMeta : AddTableMigration() {
    private class SourceMetaTable : IntIdTable() {
        val key = varchar("key", 256)
        val value = varchar("value", 4096)
        val ref = long("source_ref")
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                SourceMetaTable(),
            )
}
