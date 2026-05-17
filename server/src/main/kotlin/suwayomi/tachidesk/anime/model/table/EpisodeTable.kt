package suwayomi.tachidesk.anime.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.anime.model.dataclass.EpisodeDataClass
import suwayomi.tachidesk.manga.model.table.columns.truncatingVarchar
import suwayomi.tachidesk.manga.model.table.columns.unlimitedVarchar

object EpisodeTable : IntIdTable() {
    val url = varchar("url", 2048)
    val name = truncatingVarchar("name", 512)
    val date_upload = long("date_upload").default(0)
    val episode_number = float("episode_number").default(-1f)
    val scanlator = truncatingVarchar("scanlator", 256).nullable()
    val fillermark = bool("fillermark").default(false)
    val summary = unlimitedVarchar("summary").nullable()
    val preview_url = varchar("preview_url", 2048).nullable()

    val sourceOrder = integer("source_order")
    val fetchedAt = long("fetched_at").default(0)

    val isRead = bool("is_read").default(false)
    val isDownloaded = bool("is_downloaded").default(false)
    val lastReadAt = long("last_read_at").default(0)

    /** the real url of a episode used for the "open in WebView" feature */
    val realUrl = varchar("real_url", 2048).nullable()

    val anime = reference("anime", AnimeTable, ReferenceOption.CASCADE)
}

fun EpisodeTable.toDataClass(episodeEntry: ResultRow) = EpisodeDataClass(
    id = episodeEntry[id].value,
    url = episodeEntry[url],
    name = episodeEntry[name],
    uploadDate = episodeEntry[date_upload],
    episodeNumber = episodeEntry[episode_number],
    fillermark = episodeEntry[fillermark],
    scanlator = episodeEntry[scanlator],
    summary = episodeEntry[summary],
    previewUrl = episodeEntry[preview_url],
    animeId = episodeEntry[anime].value,
    index = episodeEntry[sourceOrder],
    fetchedAt = episodeEntry[fetchedAt],
    realUrl = episodeEntry[realUrl],
    isRead = episodeEntry[isRead],
    isDownloaded = episodeEntry[isDownloaded],
    lastReadAt = episodeEntry[lastReadAt],
)
