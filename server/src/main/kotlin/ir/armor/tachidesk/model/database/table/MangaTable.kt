package ir.armor.tachidesk.model.database.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SManga
import ir.armor.tachidesk.impl.MangaList.proxyThumbnailUrl
import ir.armor.tachidesk.model.dataclass.MangaDataClass
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow

object MangaTable : IntIdTable() {
    val url = varchar("url", 2048)
    val title = varchar("title", 512)
    val initialized = bool("initialized").default(false)

    val artist = varchar("artist", 64).nullable()
    val author = varchar("author", 64).nullable()
    val description = varchar("description", 4096).nullable()
    val genre = varchar("genre", 1024).nullable()

    //    val status = enumeration("status", MangaStatus::class).default(MangaStatus.UNKNOWN)
    val status = integer("status").default(SManga.UNKNOWN)
    val thumbnail_url = varchar("thumbnail_url", 2048).nullable()

    val inLibrary = bool("in_library").default(false)
    val defaultCategory = bool("default_category").default(true)

    // source is used by some ancestor of IntIdTable
    val sourceReference = long("source")
}

fun MangaTable.toDataClass(mangaEntry: ResultRow) =
    MangaDataClass(
        mangaEntry[MangaTable.id].value,
        mangaEntry[MangaTable.sourceReference].toString(),

        mangaEntry[MangaTable.url],
        mangaEntry[MangaTable.title],
        proxyThumbnailUrl(mangaEntry[MangaTable.id].value),

        mangaEntry[MangaTable.initialized],

        mangaEntry[MangaTable.artist],
        mangaEntry[MangaTable.author],
        mangaEntry[MangaTable.description],
        mangaEntry[MangaTable.genre],
        MangaStatus.valueOf(mangaEntry[status]).name,
        mangaEntry[MangaTable.inLibrary]
    )

enum class MangaStatus(val status: Int) {
    UNKNOWN(0),
    ONGOING(1),
    COMPLETED(2),
    LICENSED(3);

    companion object {
        fun valueOf(value: Int): MangaStatus = values().find { it.status == value } ?: UNKNOWN
    }
}
