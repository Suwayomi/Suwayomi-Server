package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeInUpdate

object CategoryTable : IntIdTable() {
    val name = varchar("name", 64)
    val order = integer("order").default(0)
    val isDefault = bool("is_default").default(false)
    val includeInUpdate = integer("include_in_update").default(IncludeInUpdate.UNSET.value)
}

fun CategoryTable.toDataClass(categoryEntry: ResultRow) = CategoryDataClass(
    categoryEntry[id].value,
    categoryEntry[order],
    categoryEntry[name],
    categoryEntry[isDefault],
    Category.getCategorySize(categoryEntry[id].value),
    IncludeInUpdate.fromValue(categoryEntry[includeInUpdate]),
    Category.getCategoryMetaMap(categoryEntry[id].value)
)
