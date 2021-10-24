package suwayomi.tachidesk.manga.model.view

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SManga
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.impl.Manga.getMangaMetaMap
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.model.dataclass.MangaViewDataClass
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.MangaStatus.Companion

/**
 * This is a dirty hack to implement View with exposed.
 * Exposed has actually no concept of a View, so it has been manually created by a sql Migration
 * Selects on this view work fine, but modifying queries are neither adviced nor tested
 * One could override the modifying functions or just don't use them
 */
object MangaView : IntIdTable("MANGA_AGGREGATED") {
    val url = varchar("url", 2048)
    val title = varchar("title", 512)
    val initialized = bool("initialized").default(false)

    val artist = varchar("artist", 512).nullable()
    val author = varchar("author", 512).nullable()
    val description = varchar("description", Integer.MAX_VALUE).nullable()
    val genre = varchar("genre", Integer.MAX_VALUE).nullable()

    val status = integer("status").default(SManga.UNKNOWN)
    val thumbnail_url = varchar("thumbnail_url", 2048).nullable()

    val inLibrary = bool("in_library").default(false)
    val defaultCategory = bool("default_category").default(true)
    val inLibraryAt = long("in_library_at").default(0)

    // the [source] field name is used by some ancestor of IntIdTable
    val sourceReference = long("source")

    /** the real url of a manga used for the "open in WebView" feature */
    val realUrl = varchar("real_url", 2048).nullable()

    val category = integer("category").nullable()
    val unread_count = integer("unread_count")
    val download_count = integer("DOWNLOAD_COUNT")
}

fun MangaView.toDataClass(mangaEntry: ResultRow) =
    MangaViewDataClass(
        mangaEntry[this.id].value,
        mangaEntry[sourceReference].toString(),

        mangaEntry[url],
        mangaEntry[title],
        proxyThumbnailUrl(mangaEntry[this.id].value),

        mangaEntry[initialized],

        mangaEntry[artist],
        mangaEntry[author],
        mangaEntry[description],
        mangaEntry[genre].toGenreList(),
        Companion.valueOf(mangaEntry[status]).name,
        mangaEntry[inLibrary],
        mangaEntry[inLibraryAt],
        meta = getMangaMetaMap(mangaEntry[id].value),
        realUrl = mangaEntry[realUrl],
        category = mangaEntry[category],
        unread_count = mangaEntry[unread_count],
        download_count = mangaEntry[download_count]
    )
