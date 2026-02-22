package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.CacheControl
import okhttp3.Response
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.getCatalogueSourceOrNull
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.anime.model.dataclass.AnimeDataClass
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.anime.model.table.toDataClass
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.InputStream

object Anime {
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val network: NetworkHelper by injectLazy()

    suspend fun getAnime(
        animeId: Int,
        onlineFetch: Boolean = false,
    ): AnimeDataClass {
        var animeEntry = transaction { AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first() }

        return if (!onlineFetch && animeEntry[AnimeTable.initialized]) {
            AnimeTable.toDataClass(animeEntry)
        } else {
            fetchAnime(animeId) ?: return AnimeTable.toDataClass(animeEntry)
            animeEntry = transaction { AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first() }
            AnimeTable.toDataClass(animeEntry)
        }
    }

    suspend fun fetchAnime(animeId: Int): SAnime? {
        val animeEntry = transaction { AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first() }

        val source = getCatalogueSourceOrNull(animeEntry[AnimeTable.sourceReference]) ?: return null
        val sAnime =
            source.getAnimeDetails(
                SAnime.create().apply {
                    url = animeEntry[AnimeTable.url]
                    title = animeEntry[AnimeTable.title]
                    thumbnail_url = animeEntry[AnimeTable.thumbnail_url]
                    background_url = animeEntry[AnimeTable.background_url]
                    artist = animeEntry[AnimeTable.artist]
                    author = animeEntry[AnimeTable.author]
                    description = animeEntry[AnimeTable.description]
                    genre = animeEntry[AnimeTable.genre]
                    status = animeEntry[AnimeTable.status]
                    update_strategy =
                        eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy.valueOf(
                            animeEntry[AnimeTable.updateStrategy],
                        )
                    fetch_type =
                        eu.kanade.tachiyomi.animesource.model.FetchType.valueOf(
                            animeEntry[AnimeTable.fetchType],
                        )
                    season_number = animeEntry[AnimeTable.seasonNumber]
                },
            )

        transaction {
            AnimeTable.update({ AnimeTable.id eq animeId }) {
                val remoteTitle =
                    try {
                        sAnime.title
                    } catch (_: UninitializedPropertyAccessException) {
                        ""
                    }
                if (remoteTitle.isNotEmpty() && remoteTitle != animeEntry[AnimeTable.title]) {
                    it[AnimeTable.title] = remoteTitle
                }
                it[AnimeTable.initialized] = true
                it[AnimeTable.artist] = sAnime.artist ?: animeEntry[AnimeTable.artist]
                it[AnimeTable.author] = sAnime.author ?: animeEntry[AnimeTable.author]
                it[AnimeTable.description] = sAnime.description ?: animeEntry[AnimeTable.description]
                it[AnimeTable.genre] = sAnime.genre ?: animeEntry[AnimeTable.genre]
                it[AnimeTable.status] = sAnime.status
                if (!sAnime.thumbnail_url.isNullOrEmpty()) {
                    it[AnimeTable.thumbnail_url] = sAnime.thumbnail_url
                }
                if (!sAnime.background_url.isNullOrEmpty()) {
                    it[AnimeTable.background_url] = sAnime.background_url
                }
                it[AnimeTable.updateStrategy] = sAnime.update_strategy.name
                it[AnimeTable.fetchType] = sAnime.fetch_type.name
                it[AnimeTable.seasonNumber] = sAnime.season_number
            }
        }

        return sAnime
    }

    private suspend fun getThumbnailUrl(
        animeId: Int,
        refreshUrl: Boolean = false,
    ): String {
        val animeEntry = transaction { AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first() }
        val thumbnailUrl = animeEntry[AnimeTable.thumbnail_url]
        if (!thumbnailUrl.isNullOrBlank() && !refreshUrl) {
            return thumbnailUrl
        }
        fetchAnime(animeId)
        val updatedEntry = transaction { AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first() }
        return updatedEntry[AnimeTable.thumbnail_url] ?: throw NullPointerException("No thumbnail found")
    }

    private suspend fun fetchHttpSourceAnimeThumbnail(
        source: AnimeHttpSource,
        animeId: Int,
        refreshUrl: Boolean = false,
    ): Response {
        val thumbnailUrl = getThumbnailUrl(animeId, refreshUrl)
        return source.client
            .newCall(GET(thumbnailUrl, source.headers, cache = CacheControl.FORCE_NETWORK))
            .awaitSuccess()
    }

    suspend fun fetchAnimeThumbnail(animeId: Int): Pair<InputStream, String> {
        val cacheSaveDir = applicationDirs.tempThumbnailCacheRoot
        val fileName = animeId.toString()

        val animeEntry = transaction { AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first() }
        val sourceId = animeEntry[AnimeTable.sourceReference]
        val source = getCatalogueSourceOrStub(sourceId)

        return when (source) {
            is AnimeHttpSource ->
                getImageResponse(cacheSaveDir, fileName) {
                    fetchHttpSourceAnimeThumbnail(source, animeId)
                }
            else ->
                getImageResponse(cacheSaveDir, fileName) {
                    val thumbnailUrl = getThumbnailUrl(animeId)
                    network.client
                        .newCall(GET(thumbnailUrl, cache = CacheControl.FORCE_NETWORK))
                        .awaitSuccess()
                }
        }
    }
}
