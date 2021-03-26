package ir.armor.tachidesk.database.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable

object CategoryMangaTable : IntIdTable() {
    val category = reference("category", CategoryTable)
    val manga = reference("manga", MangaTable)
}
