package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.network.GET
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.anime.impl.AnimeList.proxyThumbnailUrl
import suwayomi.tachidesk.anime.impl.Source.getAnimeSource
import suwayomi.tachidesk.anime.impl.util.GetAnimeHttpSource.getAnimeHttpSource
import suwayomi.tachidesk.anime.model.dataclass.AnimeDataClass
import suwayomi.tachidesk.anime.model.table.AnimeStatus
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.storage.CachedImageResponse.clearCachedImage
import suwayomi.tachidesk.manga.impl.util.storage.CachedImageResponse.getCachedImageResponse
import suwayomi.tachidesk.server.ApplicationDirs
import java.io.InputStream

object Anime {
    private fun truncate(text: String?, maxLength: Int): String? {
        return if (text?.length ?: 0 > maxLength)
            text?.take(maxLength - 3) + "..."
        else
            text
    }

    suspend fun getAnime(animeId: Int, onlineFetch: Boolean = false): AnimeDataClass {
        var animeEntry = transaction { AnimeTable.select { AnimeTable.id eq animeId }.first() }

        return if (animeEntry[AnimeTable.initialized] && !onlineFetch) {
            AnimeDataClass(
                animeId,
                animeEntry[AnimeTable.sourceReference].toString(),

                animeEntry[AnimeTable.url],
                animeEntry[AnimeTable.title],
                proxyThumbnailUrl(animeId),

                true,

                animeEntry[AnimeTable.artist],
                animeEntry[AnimeTable.author],
                animeEntry[AnimeTable.description],
                animeEntry[AnimeTable.genre],
                AnimeStatus.valueOf(animeEntry[AnimeTable.status]).name,
                animeEntry[AnimeTable.inLibrary],
                getAnimeSource(animeEntry[AnimeTable.sourceReference]),
                false
            )
        } else { // initialize anime
            val source = getAnimeHttpSource(animeEntry[AnimeTable.sourceReference])
            val fetchedAnime = source.fetchAnimeDetails(
                SAnime.create().apply {
                    url = animeEntry[AnimeTable.url]
                    title = animeEntry[AnimeTable.title]
                }
            ).awaitSingle()

            transaction {
                AnimeTable.update({ AnimeTable.id eq animeId }) {

                    it[AnimeTable.initialized] = true

                    it[AnimeTable.artist] = fetchedAnime.artist
                    it[AnimeTable.author] = fetchedAnime.author
                    it[AnimeTable.description] = truncate(fetchedAnime.description, 4096)
                    it[AnimeTable.genre] = fetchedAnime.genre
                    it[AnimeTable.status] = fetchedAnime.status
                    if (fetchedAnime.thumbnail_url != null && fetchedAnime.thumbnail_url.orEmpty().isNotEmpty())
                        it[AnimeTable.thumbnail_url] = fetchedAnime.thumbnail_url
                }
            }

            clearAnimeThumbnail(animeId)

            animeEntry = transaction { AnimeTable.select { AnimeTable.id eq animeId }.first() }

            AnimeDataClass(
                animeId,
                animeEntry[AnimeTable.sourceReference].toString(),

                animeEntry[AnimeTable.url],
                animeEntry[AnimeTable.title],
                proxyThumbnailUrl(animeId),

                true,

                fetchedAnime.artist,
                fetchedAnime.author,
                fetchedAnime.description,
                fetchedAnime.genre,
                AnimeStatus.valueOf(fetchedAnime.status).name,
                animeEntry[AnimeTable.inLibrary],
                getAnimeSource(animeEntry[AnimeTable.sourceReference]),
                true
            )
        }
    }

    private val applicationDirs by DI.global.instance<ApplicationDirs>()
    suspend fun getAnimeThumbnail(animeId: Int): Pair<InputStream, String> {
        val saveDir = applicationDirs.animeThumbnailsRoot
        val fileName = animeId.toString()

        return getCachedImageResponse(saveDir, fileName) {
            getAnime(animeId) // make sure is initialized

            val animeEntry = transaction { AnimeTable.select { AnimeTable.id eq animeId }.first() }

            val sourceId = animeEntry[AnimeTable.sourceReference]
            val source = getAnimeHttpSource(sourceId)

            val thumbnailUrl = animeEntry[AnimeTable.thumbnail_url]!!

            source.client.newCall(
                GET(thumbnailUrl, source.headers)
            ).await()
        }
    }

    private fun clearAnimeThumbnail(animeId: Int) {
        val saveDir = applicationDirs.animeThumbnailsRoot
        val fileName = animeId.toString()

        clearCachedImage(saveDir, fileName)
    }
}
