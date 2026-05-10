package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object MangaExcludedScanlatorTable : Table() {
    val manga = reference("manga_id", MangaTable, ReferenceOption.CASCADE)
    val scanlator = varchar("scanlator", 512)

    override val primaryKey = PrimaryKey(manga, scanlator)
}
