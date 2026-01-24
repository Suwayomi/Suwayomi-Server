package suwayomi.tachidesk.anime.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.anime.model.dataclass.AnimeDataClass
import suwayomi.tachidesk.anime.impl.AnimeList
import suwayomi.tachidesk.anime.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.columns.truncatingVarchar
import suwayomi.tachidesk.manga.model.table.columns.unlimitedVarchar

object AnimeTable : IntIdTable() {
    val url = varchar("url", 2048)
    val title = truncatingVarchar("title", 512)
    val initialized = bool("initialized").default(false)

    val artist = unlimitedVarchar("artist").nullable()
    val author = unlimitedVarchar("author").nullable()
    val description = unlimitedVarchar("description").nullable()
    val genre = unlimitedVarchar("genre").nullable()

    val status = integer("status").default(SAnime.UNKNOWN)
    val thumbnail_url = varchar("thumbnail_url", 2048).nullable()
    val background_url = varchar("background_url", 2048).nullable()

    val inLibrary = bool("in_library").default(false)
    val inLibraryAt = long("in_library_at").default(0)

    // the [source] field name is used by some ancestor of IntIdTable
    val sourceReference = long("source")

    val updateStrategy = varchar("update_strategy", 256).default(AnimeUpdateStrategy.ALWAYS_UPDATE.name)
    val fetchType = varchar("fetch_type", 256).default(FetchType.Episodes.name)
    val seasonNumber = double("season_number").default(-1.0)
}

fun AnimeTable.toDataClass(animeEntry: ResultRow) = AnimeDataClass(
    id = animeEntry[id].value,
    sourceId = animeEntry[sourceReference].toString(),
    url = animeEntry[url],
    title = animeEntry[title],
    thumbnailUrl = AnimeList.proxyThumbnailUrl(animeEntry[id].value),
    backgroundUrl = animeEntry[background_url],
    initialized = animeEntry[initialized],
    artist = animeEntry[artist],
    author = animeEntry[author],
    description = animeEntry[description],
    genre = animeEntry[genre].toGenreList(),
    status = AnimeStatus.valueOf(animeEntry[status]).name,
    inLibrary = animeEntry[inLibrary],
    inLibraryAt = animeEntry[inLibraryAt],
    updateStrategy = AnimeUpdateStrategy.valueOf(animeEntry[updateStrategy]),
    fetchType = FetchType.valueOf(animeEntry[fetchType]),
    seasonNumber = animeEntry[seasonNumber],
)

enum class AnimeStatus(
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
        fun valueOf(value: Int): AnimeStatus = entries.find { it.value == value } ?: UNKNOWN
    }
}
