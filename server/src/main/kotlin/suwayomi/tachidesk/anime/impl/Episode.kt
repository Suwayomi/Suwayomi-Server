package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.jetbrains.exposed.sql.SortOrder.DESC
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.anime.impl.Anime.getAnime
import suwayomi.tachidesk.anime.impl.util.GetAnimeHttpSource.getAnimeHttpSource
import suwayomi.tachidesk.anime.model.dataclass.EpisodeDataClass
import suwayomi.tachidesk.anime.model.dataclass.VideoDataClass
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.anime.model.table.EpisodeTable
import suwayomi.tachidesk.anime.model.table.toDataClass
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle

object Episode {
    /** get episode list when showing an anime */
    suspend fun getEpisodeList(animeId: Int, onlineFetch: Boolean?): List<EpisodeDataClass> {
        return if (onlineFetch == true) {
            getSourceEpisodes(animeId)
        } else {
            transaction {
                EpisodeTable.select { EpisodeTable.anime eq animeId }.orderBy(EpisodeTable.episodeIndex to DESC)
                    .map {
                        EpisodeTable.toDataClass(it)
                    }
            }.ifEmpty {
                // If it was explicitly set to offline dont grab episodes
                if (onlineFetch == null) {
                    getSourceEpisodes(animeId)
                } else emptyList()
            }
        }
    }

    private suspend fun getSourceEpisodes(animeId: Int): List<EpisodeDataClass> {
        val animeDetails = getAnime(animeId)
        val source = getAnimeHttpSource(animeDetails.sourceId.toLong())
        val episodeList = source.fetchEpisodeList(
            SAnime.create().apply {
                title = animeDetails.title
                url = animeDetails.url
            }
        ).awaitSingle()

        val episodeCount = episodeList.count()

        transaction {
            episodeList.reversed().forEachIndexed { index, fetchedEpisode ->
                val episodeEntry = EpisodeTable.select { EpisodeTable.url eq fetchedEpisode.url }.firstOrNull()
                if (episodeEntry == null) {
                    EpisodeTable.insert {
                        it[url] = fetchedEpisode.url
                        it[name] = fetchedEpisode.name
                        it[date_upload] = fetchedEpisode.date_upload
                        it[episode_number] = fetchedEpisode.episode_number
                        it[scanlator] = fetchedEpisode.scanlator

                        it[episodeIndex] = index + 1
                        it[anime] = animeId
                    }
                } else {
                    EpisodeTable.update({ EpisodeTable.url eq fetchedEpisode.url }) {
                        it[name] = fetchedEpisode.name
                        it[date_upload] = fetchedEpisode.date_upload
                        it[episode_number] = fetchedEpisode.episode_number
                        it[scanlator] = fetchedEpisode.scanlator

                        it[episodeIndex] = index + 1
                        it[anime] = animeId
                    }
                }
            }
        }

        // clear any orphaned episodes that are in the db but not in `episodeList`
        val dbEpisodeCount = transaction { EpisodeTable.select { EpisodeTable.anime eq animeId }.count() }
        if (dbEpisodeCount > episodeCount) { // we got some clean up due
            val dbEpisodeList = transaction { EpisodeTable.select { EpisodeTable.anime eq animeId } }

            dbEpisodeList.forEach {
                if (it[EpisodeTable.episodeIndex] >= episodeList.size ||
                    episodeList[it[EpisodeTable.episodeIndex] - 1].url != it[EpisodeTable.url]
                ) {
                    transaction {
//                        PageTable.deleteWhere { PageTable.episode eq it[EpisodeTable.id] }
                        EpisodeTable.deleteWhere { EpisodeTable.id eq it[EpisodeTable.id] }
                    }
                }
            }
        }

        val dbEpisodeMap = transaction {
            EpisodeTable.select { EpisodeTable.anime eq animeId }
                .associateBy({ it[EpisodeTable.url] }, { it })
        }

        return episodeList.mapIndexed { index, it ->

            val dbEpisode = dbEpisodeMap.getValue(it.url)

            EpisodeDataClass(
                it.url,
                it.name,
                it.date_upload,
                it.episode_number,
                it.scanlator,
                animeId,

                dbEpisode[EpisodeTable.isRead],
                dbEpisode[EpisodeTable.isBookmarked],
                dbEpisode[EpisodeTable.lastPageRead],

                episodeCount - index,
                episodeList.size
            )
        }
    }

    /** used to display a episode, get a episode in order to show it's video */
    suspend fun getEpisode(episodeIndex: Int, animeId: Int): EpisodeDataClass {
        val episode = getEpisodeList(animeId, false)
            .first { it.index == episodeIndex }

        val animeEntry = transaction { AnimeTable.select { AnimeTable.id eq animeId }.first() }
        val source = getAnimeHttpSource(animeEntry[AnimeTable.sourceReference])
        val fetchedVideos = source.fetchVideoList(
            SEpisode.create().also {
                it.url = episode.url
                it.name = episode.name
            }
        ).awaitSingle()

        return EpisodeDataClass(
            episode.url,
            episode.name,
            episode.uploadDate,
            episode.episodeNumber,
            episode.scanlator,
            animeId,
            episode.read,
            episode.bookmarked,
            episode.lastPageRead,
            episode.index,
            episode.episodeCount,
            fetchedVideos.map {
                VideoDataClass(
                    it.url,
                    it.quality,
                    it.videoUrl,
                )
            }
        )
    }

//    /** used to display a episode, get a episode in order to show it's pages */
//    suspend fun getEpisode(episodeIndex: Int, animeId: Int): EpisodeDataClass {
//        val episodeEntry = transaction {
//            EpisodeTable.select {
//                (EpisodeTable.episodeIndex eq episodeIndex) and (EpisodeTable.anime eq animeId)
//            }.first()
//        }
//        val animeEntry = transaction { MangaTable.select { MangaTable.id eq animeId }.first() }
//        val source = getAnimeHttpSource(animeEntry[MangaTable.sourceReference])
//
//        val pageList = source.fetchPageList(
//            SEpisode.create().apply {
//                url = episodeEntry[EpisodeTable.url]
//                name = episodeEntry[EpisodeTable.name]
//            }
//        ).awaitSingle()
//
//        val episodeId = episodeEntry[EpisodeTable.id].value
//        val episodeCount = transaction { EpisodeTable.select { EpisodeTable.anime eq animeId }.count() }
//
//        // update page list for this episode
//        transaction {
//            pageList.forEach { page ->
//                val pageEntry = transaction { PageTable.select { (PageTable.episode eq episodeId) and (PageTable.index eq page.index) }.firstOrNull() }
//                if (pageEntry == null) {
//                    PageTable.insert {
//                        it[index] = page.index
//                        it[url] = page.url
//                        it[imageUrl] = page.imageUrl
//                        it[episode] = episodeId
//                    }
//                } else {
//                    PageTable.update({ (PageTable.episode eq episodeId) and (PageTable.index eq page.index) }) {
//                        it[url] = page.url
//                        it[imageUrl] = page.imageUrl
//                    }
//                }
//            }
//        }
//
//        return EpisodeDataClass(
//            episodeEntry[EpisodeTable.url],
//            episodeEntry[EpisodeTable.name],
//            episodeEntry[EpisodeTable.date_upload],
//            episodeEntry[EpisodeTable.episode_number],
//            episodeEntry[EpisodeTable.scanlator],
//            animeId,
//            episodeEntry[EpisodeTable.isRead],
//            episodeEntry[EpisodeTable.isBookmarked],
//            episodeEntry[EpisodeTable.lastPageRead],
//
//            episodeEntry[EpisodeTable.episodeIndex],
//            episodeCount.toInt(),
//            pageList.count()
//        )
//    }

    fun modifyEpisode(animeId: Int, episodeIndex: Int, isRead: Boolean?, isBookmarked: Boolean?, markPrevRead: Boolean?, lastPageRead: Int?) {
        transaction {
            if (listOf(isRead, isBookmarked, lastPageRead).any { it != null }) {
                EpisodeTable.update({ (EpisodeTable.anime eq animeId) and (EpisodeTable.episodeIndex eq episodeIndex) }) { update ->
                    isRead?.also {
                        update[EpisodeTable.isRead] = it
                    }
                    isBookmarked?.also {
                        update[EpisodeTable.isBookmarked] = it
                    }
                    lastPageRead?.also {
                        update[EpisodeTable.lastPageRead] = it
                    }
                }
            }

            markPrevRead?.let {
                EpisodeTable.update({ (EpisodeTable.anime eq animeId) and (EpisodeTable.episodeIndex less episodeIndex) }) {
                    it[EpisodeTable.isRead] = markPrevRead
                }
            }
        }
    }
}
