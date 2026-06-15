package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude

object CategoryTable : IntIdTable() {
    val name = varchar("name", 64)
    val order = integer("sort_order").default(0)
    val isDefault = bool("is_default").default(false)
    val includeInUpdate = integer("include_in_update").default(IncludeOrExclude.UNSET.value)
    val includeInDownload = integer("include_in_download").default(IncludeOrExclude.UNSET.value)

    val version = long("version").default(0)
    val uid = long("uid").default(0)
    val lastModifiedAt = long("last_modified_at").default(0)
    val isSyncing = bool("is_syncing").default(false)
}

fun CategoryTable.toDataClass(categoryEntry: ResultRow) =
    CategoryDataClass(
        id = categoryEntry[id].value,
        order = categoryEntry[order],
        name = categoryEntry[name],
        default = categoryEntry[isDefault],
        includeInUpdate = IncludeOrExclude.fromValue(categoryEntry[includeInUpdate]),
        includeInDownload = IncludeOrExclude.fromValue(categoryEntry[includeInDownload]),
        version = categoryEntry[version],
        uid = categoryEntry[uid],
        lastModifiedAt = categoryEntry[lastModifiedAt],
    )
