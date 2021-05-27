package suwayomi.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.AnimesPage
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.anime.impl.util.GetAnimeHttpSource.getAnimeHttpSource
import suwayomi.anime.model.dataclass.AnimeDataClass
import suwayomi.anime.model.dataclass.PagedAnimeListDataClass
import suwayomi.anime.model.table.AnimeStatus
import suwayomi.anime.model.table.AnimeTable
import suwayomi.tachidesk.impl.util.lang.awaitSingle

object AnimeList {
    fun proxyThumbnailUrl(animeId: Int): String {
        return "/api/v1/anime/anime/$animeId/thumbnail"
    }

    suspend fun getAnimeList(sourceId: Long, pageNum: Int = 1, popular: Boolean): PagedAnimeListDataClass {
        val source = getAnimeHttpSource(sourceId)
        val animesPage = if (popular) {
            source.fetchPopularAnime(pageNum).awaitSingle()
        } else {
            if (source.supportsLatest)
                source.fetchLatestUpdates(pageNum).awaitSingle()
            else
                throw Exception("Source $source doesn't support latest")
        }
        return animesPage.processEntries(sourceId)
    }

    fun AnimesPage.processEntries(sourceId: Long): PagedAnimeListDataClass {
        val animesPage = this
        val animeList = transaction {
            return@transaction animesPage.animes.map { anime ->
                val animeEntry = AnimeTable.select { AnimeTable.url eq anime.url }.firstOrNull()
                if (animeEntry == null) { // create anime entry
                    val animeId = AnimeTable.insertAndGetId {
                        it[url] = anime.url
                        it[title] = anime.title

                        it[artist] = anime.artist
                        it[author] = anime.author
                        it[description] = anime.description
                        it[genre] = anime.genre
                        it[status] = anime.status
                        it[thumbnail_url] = anime.thumbnail_url

                        it[sourceReference] = sourceId
                    }.value

                    AnimeDataClass(
                        animeId,
                        sourceId.toString(),

                        anime.url,
                        anime.title,
                        proxyThumbnailUrl(animeId),

                        anime.initialized,

                        anime.artist,
                        anime.author,
                        anime.description,
                        anime.genre,
                        AnimeStatus.valueOf(anime.status).name
                    )
                } else {
                    val animeId = animeEntry[AnimeTable.id].value
                    AnimeDataClass(
                        animeId,
                        sourceId.toString(),

                        anime.url,
                        anime.title,
                        proxyThumbnailUrl(animeId),

                        true,

                        animeEntry[AnimeTable.artist],
                        animeEntry[AnimeTable.author],
                        animeEntry[AnimeTable.description],
                        animeEntry[AnimeTable.genre],
                        AnimeStatus.valueOf(animeEntry[AnimeTable.status]).name,
                        animeEntry[AnimeTable.inLibrary]
                    )
                }
            }
        }
        return PagedAnimeListDataClass(
            animeList,
            animesPage.hasNextPage
        )
    }
}
