package suwayomi.anime.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.SAnime
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.model.dataclass.MangaDataClass
import suwayomi.tachidesk.model.table.MangaStatus.Companion

object AnimeTable : IntIdTable() {
    val url = varchar("url", 2048)
    val title = varchar("title", 512)
    val initialized = bool("initialized").default(false)

    val artist = varchar("artist", 64).nullable()
    val author = varchar("author", 64).nullable()
    val description = varchar("description", 4096).nullable()
    val genre = varchar("genre", 1024).nullable()

    val status = integer("status").default(SAnime.UNKNOWN)
    val thumbnail_url = varchar("thumbnail_url", 2048).nullable()

    val inLibrary = bool("in_library").default(false)
    val defaultCategory = bool("default_category").default(true)

    // source is used by some ancestor of IntIdTable
    val sourceReference = long("source")
}

fun AnimeTable.toDataClass(mangaEntry: ResultRow) =
    MangaDataClass(
        mangaEntry[this.id].value,
        mangaEntry[sourceReference].toString(),

        mangaEntry[url],
        mangaEntry[title],
        proxyThumbnailUrl(mangaEntry[this.id].value),

        mangaEntry[initialized],

        mangaEntry[artist],
        mangaEntry[author],
        mangaEntry[description],
        mangaEntry[genre],
        Companion.valueOf(mangaEntry[status]).name,
        mangaEntry[inLibrary]
    )

enum class AnimeStatus(val status: Int) {
    UNKNOWN(0),
    ONGOING(1),
    COMPLETED(2),
    LICENSED(3);

    companion object {
        fun valueOf(value: Int): AnimeStatus = values().find { it.status == value } ?: UNKNOWN
    }
}
