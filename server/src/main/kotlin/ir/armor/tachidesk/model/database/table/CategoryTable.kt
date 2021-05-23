package ir.armor.tachidesk.model.database.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.model.dataclass.CategoryDataClass
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow

object CategoryTable : IntIdTable() {
    val name = varchar("name", 64)
    val order = integer("order").default(0)
    val isDefault = bool("is_default").default(false)
}

fun CategoryTable.toDataClass(categoryEntry: ResultRow) = CategoryDataClass(
    categoryEntry[this.id].value,
    categoryEntry[this.order],
    categoryEntry[this.name],
    categoryEntry[this.isDefault],
)
