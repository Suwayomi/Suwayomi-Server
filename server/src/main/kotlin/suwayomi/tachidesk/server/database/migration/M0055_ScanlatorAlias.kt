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
class M0055_ScanlatorAlias : AddTableMigration() {
    private class ScanlatorAliasTable : IntIdTable() {
        val scanlator = varchar("scanlator", 256).uniqueIndex()
        val displayName = varchar("display_name", 256)
        val createdAt = long("created_at")
        val updatedAt = long("updated_at")
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                ScanlatorAliasTable(),
            )
}
