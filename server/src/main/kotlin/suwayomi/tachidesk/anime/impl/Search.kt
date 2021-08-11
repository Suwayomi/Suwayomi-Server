package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.anime.impl.AnimeList.processEntries
import suwayomi.tachidesk.anime.impl.util.GetAnimeHttpSource.getAnimeHttpSource
import suwayomi.tachidesk.anime.model.dataclass.PagedAnimeListDataClass
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle

object Search {
    suspend fun sourceSearch(sourceId: Long, searchTerm: String, pageNum: Int): PagedAnimeListDataClass {
        val source = getAnimeHttpSource(sourceId)
        val searchManga = source.fetchSearchAnime(pageNum, searchTerm, source.getFilterList()).awaitSingle()
        return searchManga.processEntries(sourceId)
    }
}
