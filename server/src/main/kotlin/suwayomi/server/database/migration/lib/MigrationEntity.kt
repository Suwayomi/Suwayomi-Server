package suwayomi.server.database.migration.lib

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// originally licenced under MIT by Andreas Mausch, Changes are licenced under Mozilla Public License, v. 2.0.
// adopted from: https://gitlab.com/andreas-mausch/exposed-migrations/-/tree/4bf853c18a24d0170eda896ddbb899cb01233595

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.`java-time`.timestamp

object MigrationsTable : IdTable<Int>() {
    override val id = integer("version").entityId()
    override val primaryKey = PrimaryKey(id)

    val name = varchar("name", length = 400)
    val executedAt = timestamp("executed_at")

    init {
        index(true, name)
    }
}

class MigrationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MigrationEntity>(MigrationsTable)

    var version by MigrationsTable.id
    var name by MigrationsTable.name
    var executedAt by MigrationsTable.executedAt
}
