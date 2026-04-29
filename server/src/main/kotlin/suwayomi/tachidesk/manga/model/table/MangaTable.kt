package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.impl.Manga.getMangaMetaMap
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.impl.MangaUserOverride
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.MangaStatus.Companion
import suwayomi.tachidesk.manga.model.table.columns.truncatingVarchar
import suwayomi.tachidesk.manga.model.table.columns.unlimitedVarchar

object MangaTable : IntIdTable() {
    val url = varchar("url", 2048)
    val title = truncatingVarchar("title", 512)
    val initialized = bool("initialized").default(false)

    val artist = unlimitedVarchar("artist").nullable()
    val author = unlimitedVarchar("author").nullable()
    val description = unlimitedVarchar("description").nullable()
    val genre = unlimitedVarchar("genre").nullable()

    val status = integer("status").default(SManga.UNKNOWN)
    val thumbnail_url = varchar("thumbnail_url", 2048).nullable()
    val thumbnailUrlLastFetched = long("thumbnail_url_last_fetched").default(0)

    val inLibrary = bool("in_library").default(false)
    val inLibraryAt = long("in_library_at").default(0)

    // the [source] field name is used by some ancestor of IntIdTable
    val sourceReference = long("source")

    /** the real url of a manga used for the "open in WebView" feature */
    val realUrl = varchar("real_url", 2048).nullable()

    val lastFetchedAt = long("last_fetched_at").default(0)
    val chaptersLastFetchedAt = long("chapters_last_fetched_at").default(0)

    val updateStrategy = varchar("update_strategy", 256).default(UpdateStrategy.ALWAYS_UPDATE.name)
}

fun MangaTable.toDataClass(
    mangaEntry: ResultRow,
    includeMangaMeta: Boolean = true,
): MangaDataClass {
    val mangaId = mangaEntry[this.id].value
    val override = MangaUserOverride.cachedOverride(mangaId)
    return MangaDataClass(
        id = mangaId,
        sourceId = mangaEntry[sourceReference].toString(),
        url = mangaEntry[url],
        title = override?.title?.takeIf { it.isNotBlank() } ?: mangaEntry[title],
        thumbnailUrl = proxyThumbnailUrl(mangaId),
        thumbnailUrlLastFetched = mangaEntry[thumbnailUrlLastFetched],
        initialized = mangaEntry[initialized],
        artist = override?.artist?.takeIf { it.isNotBlank() } ?: mangaEntry[artist],
        author = override?.author?.takeIf { it.isNotBlank() } ?: mangaEntry[author],
        description =
            override?.description?.takeIf { it.isNotBlank() } ?: mangaEntry[description],
        genre =
            override?.genre?.takeIf { it.isNotEmpty() } ?: mangaEntry[genre].toGenreList(),
        status = Companion.valueOf(mangaEntry[status]).name,
        inLibrary = mangaEntry[inLibrary],
        inLibraryAt = mangaEntry[inLibraryAt],
        meta =
            if (includeMangaMeta) {
                getMangaMetaMap(mangaId)
            } else {
                emptyMap()
            },
        realUrl = mangaEntry[realUrl],
        lastFetchedAt = mangaEntry[lastFetchedAt],
        chaptersLastFetchedAt = mangaEntry[chaptersLastFetchedAt],
        updateStrategy = UpdateStrategy.valueOf(mangaEntry[updateStrategy]),
    )
}

enum class MangaStatus(
    val value: Int,
) {
    UNKNOWN(0),
    ONGOING(1),
    COMPLETED(2),
    LICENSED(3),
    PUBLISHING_FINISHED(4),
    CANCELLED(5),
    ON_HIATUS(6),
    ;

    companion object {
        fun valueOf(value: Int): MangaStatus = entries.find { it.value == value } ?: UNKNOWN
    }
}
