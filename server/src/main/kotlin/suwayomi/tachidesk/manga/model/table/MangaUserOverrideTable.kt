package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.model.dataclass.MangaUserOverrideDataClass

object MangaUserOverrideTable : IntIdTable() {
    val mangaRef = reference("manga_ref", MangaTable, ReferenceOption.CASCADE).uniqueIndex()
    val title = varchar("title", 512).nullable()
    val author = varchar("author", 256).nullable()
    val artist = varchar("artist", 256).nullable()
    val description = text("description").nullable()
    val genre = text("genre").nullable() // comma-separated, mirrors MangaTable.genre
    val notes = text("notes").nullable()
    val hasCustomCover = bool("has_custom_cover").default(false)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

fun MangaUserOverrideTable.toDataClass(row: ResultRow) =
    MangaUserOverrideDataClass(
        id = row[id].value,
        mangaId = row[mangaRef].value,
        title = row[title],
        author = row[author],
        artist = row[artist],
        description = row[description],
        genre = row[genre]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
        notes = row[notes],
        hasCustomCover = row[hasCustomCover],
        createdAt = row[createdAt],
        updatedAt = row[updatedAt],
    )
