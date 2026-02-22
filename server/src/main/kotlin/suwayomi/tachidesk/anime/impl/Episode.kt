package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.dao.id.EntityID
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.anime.model.dataclass.EpisodeDataClass
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.anime.model.table.EpisodeTable
import suwayomi.tachidesk.anime.model.table.toDataClass
import java.time.Instant

object Episode {
    private val mutex = Mutex()

    /** get episode list when showing an anime */
    suspend fun getEpisodeList(
        animeId: Int,
        onlineFetch: Boolean = false,
    ): List<EpisodeDataClass> =
        if (onlineFetch) {
            getSourceEpisodes(animeId)
        } else {
            transaction {
                EpisodeTable
                    .selectAll()
                    .where { EpisodeTable.anime eq animeId }
                    .orderBy(EpisodeTable.sourceOrder to SortOrder.DESC)
                    .map { EpisodeTable.toDataClass(it) }
            }.ifEmpty {
                getSourceEpisodes(animeId)
            }
        }

    suspend fun getEpisodeByIndex(
        animeId: Int,
        episodeIndex: Int,
    ): EpisodeDataClass {
        val entry =
            transaction {
                EpisodeTable
                    .selectAll()
                    .where { (EpisodeTable.anime eq animeId) and (EpisodeTable.sourceOrder eq episodeIndex) }
                    .firstOrNull()
            }
        if (entry != null) {
            return EpisodeTable.toDataClass(entry)
        }

        getSourceEpisodes(animeId)

        return transaction {
            EpisodeTable
                .selectAll()
                .where { (EpisodeTable.anime eq animeId) and (EpisodeTable.sourceOrder eq episodeIndex) }
                .first()
                .let { EpisodeTable.toDataClass(it) }
        }
    }

    private suspend fun getSourceEpisodes(animeId: Int): List<EpisodeDataClass> {
        fetchEpisodeList(animeId)
        return transaction {
            EpisodeTable
                .selectAll()
                .where { EpisodeTable.anime eq animeId }
                .orderBy(EpisodeTable.sourceOrder to SortOrder.DESC)
                .map { EpisodeTable.toDataClass(it) }
        }
    }

    suspend fun fetchEpisodeList(animeId: Int): List<SEpisode> {
        return mutex.withLock {
            val animeEntry = transaction { AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first() }
            val source = getCatalogueSourceOrStub(animeEntry[AnimeTable.sourceReference])

            val sAnime =
                SAnime.create().apply {
                    url = animeEntry[AnimeTable.url]
                    title = animeEntry[AnimeTable.title]
                    artist = animeEntry[AnimeTable.artist]
                    author = animeEntry[AnimeTable.author]
                    description = animeEntry[AnimeTable.description]
                    genre = animeEntry[AnimeTable.genre]
                    status = animeEntry[AnimeTable.status]
                    thumbnail_url = animeEntry[AnimeTable.thumbnail_url]
                    background_url = animeEntry[AnimeTable.background_url]
                    update_strategy = eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy.valueOf(
                        animeEntry[AnimeTable.updateStrategy],
                    )
                    fetch_type = eu.kanade.tachiyomi.animesource.model.FetchType.valueOf(animeEntry[AnimeTable.fetchType])
                    season_number = animeEntry[AnimeTable.seasonNumber]
                }

            val episodes = source.getEpisodeList(sAnime)
            val uniqueEpisodes = episodes.distinctBy { it.url }

            if (uniqueEpisodes.isEmpty()) {
                throw Exception("No episodes found")
            }

            uniqueEpisodes.forEach { episode ->
                (source as? AnimeHttpSource)?.prepareNewEpisode(episode, sAnime)
                episode.scanlator = episode.scanlator?.ifBlank { null }?.trim()
            }

            val now = Instant.now().epochSecond

            val episodesInDb =
                transaction {
                    EpisodeTable
                        .selectAll()
                        .where { EpisodeTable.anime eq animeId }
                        .associateBy { it[EpisodeTable.url] }
                }

            val episodesToInsert = mutableListOf<EpisodeDataClass>()
            val episodesToUpdate = mutableListOf<EpisodeDataClass>()

            uniqueEpisodes.reversed().forEachIndexed { index, fetchedEpisode ->
                val episodeEntry = episodesInDb[fetchedEpisode.url]
                val isRead = episodeEntry?.get(EpisodeTable.isRead) ?: false
                val isDownloaded = episodeEntry?.get(EpisodeTable.isDownloaded) ?: false
                val lastReadAt = episodeEntry?.get(EpisodeTable.lastReadAt) ?: 0
                val realUrl = runCatching { (source as? AnimeHttpSource)?.getEpisodeUrl(fetchedEpisode) }.getOrNull()

                val episodeData =
                    EpisodeDataClass.fromSEpisode(
                        fetchedEpisode,
                        episodeEntry?.get(EpisodeTable.id)?.value ?: 0,
                        index + 1,
                        now,
                        animeId,
                        realUrl,
                        isRead,
                        isDownloaded,
                        lastReadAt,
                    )

                if (episodeEntry == null) {
                    episodesToInsert.add(episodeData)
                } else {
                    episodesToUpdate.add(episodeData)
                }
            }

            transaction {
                if (episodesToInsert.isNotEmpty()) {
                    EpisodeTable.batchInsert(episodesToInsert) { episode ->
                        this[EpisodeTable.url] = episode.url
                        this[EpisodeTable.name] = episode.name
                        this[EpisodeTable.date_upload] = episode.uploadDate
                        this[EpisodeTable.episode_number] = episode.episodeNumber
                        this[EpisodeTable.scanlator] = episode.scanlator
                        this[EpisodeTable.fillermark] = episode.fillermark
                        this[EpisodeTable.summary] = episode.summary
                        this[EpisodeTable.preview_url] = episode.previewUrl
                        this[EpisodeTable.sourceOrder] = episode.index
                        this[EpisodeTable.fetchedAt] = episode.fetchedAt
                        this[EpisodeTable.realUrl] = episode.realUrl
                        this[EpisodeTable.isRead] = episode.isRead
                        this[EpisodeTable.isDownloaded] = episode.isDownloaded
                        this[EpisodeTable.lastReadAt] = episode.lastReadAt
                        this[EpisodeTable.anime] = EntityID(animeId, AnimeTable)
                    }
                }

                if (episodesToUpdate.isNotEmpty()) {
                    BatchUpdateStatement(EpisodeTable).apply {
                        episodesToUpdate.forEach { episode ->
                            addBatch(EntityID(episode.id, EpisodeTable))
                            this[EpisodeTable.name] = episode.name
                            this[EpisodeTable.date_upload] = episode.uploadDate
                            this[EpisodeTable.episode_number] = episode.episodeNumber
                            this[EpisodeTable.scanlator] = episode.scanlator
                            this[EpisodeTable.fillermark] = episode.fillermark
                            this[EpisodeTable.summary] = episode.summary
                            this[EpisodeTable.preview_url] = episode.previewUrl
                            this[EpisodeTable.sourceOrder] = episode.index
                            this[EpisodeTable.fetchedAt] = episode.fetchedAt
                            this[EpisodeTable.realUrl] = episode.realUrl
                            this[EpisodeTable.isRead] = episode.isRead
                            this[EpisodeTable.isDownloaded] = episode.isDownloaded
                            this[EpisodeTable.lastReadAt] = episode.lastReadAt
                        }
                        execute(this@transaction)
                    }
                }
            }

            uniqueEpisodes
        }
    }

    @Serializable
    data class EpisodeChange(
        val isRead: Boolean? = null,
        val isDownloaded: Boolean? = null,
    )

    @Serializable
    data class EpisodeBatchEditInput(
        val episodeIds: List<Int>? = null,
        val change: EpisodeChange?,
    )

    fun modifyEpisodes(input: EpisodeBatchEditInput) {
        val change = input.change ?: return
        val (isRead, isDownloaded) = change

        if (listOfNotNull(isRead, isDownloaded).isEmpty()) return
        val episodeIds = input.episodeIds ?: return

        transaction {
            val now = Instant.now().epochSecond
            EpisodeTable.update({ EpisodeTable.id inList episodeIds }) { update ->
                isRead?.also {
                    update[EpisodeTable.isRead] = it
                    update[EpisodeTable.lastReadAt] = if (it) now else 0
                }
                isDownloaded?.also {
                    update[EpisodeTable.isDownloaded] = it
                }
            }
        }
    }
}
