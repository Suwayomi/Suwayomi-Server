package suwayomi.anime.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.anime.model.dataclass.EpisodeDataClass

object EpisodeTable : IntIdTable() {
    val url = varchar("url", 2048)
    val name = varchar("name", 512)
    val date_upload = long("date_upload").default(0)
    val episode_number = float("episode_number").default(-1f)
    val scanlator = varchar("scanlator", 128).nullable()

    val isRead = bool("read").default(false)
    val isBookmarked = bool("bookmark").default(false)
    val lastPageRead = integer("last_page_read").default(0)

    // index is reserved by a function
    val episodeIndex = integer("index")

    val anime = reference("anime", AnimeTable)
}

fun EpisodeTable.toDataClass(episodeEntry: ResultRow) =
    EpisodeDataClass(
        episodeEntry[url],
        episodeEntry[name],
        episodeEntry[date_upload],
        episodeEntry[episode_number],
        episodeEntry[scanlator],
        episodeEntry[anime].value,
        episodeEntry[isRead],
        episodeEntry[isBookmarked],
        episodeEntry[lastPageRead],
        episodeEntry[episodeIndex],
    )
