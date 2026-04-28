package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.model.dataclass.ScanlatorAliasDataClass

object ScanlatorAliasTable : IntIdTable() {
    val scanlator = varchar("scanlator", 256).uniqueIndex()
    val displayName = varchar("display_name", 256)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

fun ScanlatorAliasTable.toDataClass(row: ResultRow) =
    ScanlatorAliasDataClass(
        id = row[id].value,
        scanlator = row[scanlator],
        displayName = row[displayName],
        createdAt = row[createdAt],
        updatedAt = row[updatedAt],
    )
