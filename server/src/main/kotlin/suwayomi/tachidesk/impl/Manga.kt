package suwayomi.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.server.ApplicationDirs
import suwayomi.tachidesk.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.impl.Source.getSource
import suwayomi.tachidesk.impl.util.GetHttpSource.getHttpSource
import suwayomi.tachidesk.impl.util.lang.awaitSingle
import suwayomi.tachidesk.impl.util.network.await
import suwayomi.tachidesk.impl.util.storage.CachedImageResponse.clearCachedImage
import suwayomi.tachidesk.impl.util.storage.CachedImageResponse.getCachedImageResponse
import suwayomi.tachidesk.model.dataclass.MangaDataClass
import suwayomi.tachidesk.model.table.MangaStatus
import suwayomi.tachidesk.model.table.MangaTable
import java.io.InputStream

object Manga {
    private fun truncate(text: String?, maxLength: Int): String? {
        return if (text?.length ?: 0 > maxLength)
            text?.take(maxLength - 3) + "..."
        else
            text
    }

    suspend fun getManga(mangaId: Int, onlineFetch: Boolean = false): MangaDataClass {
        var mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }

        return if (mangaEntry[MangaTable.initialized] && !onlineFetch) {
            MangaDataClass(
                mangaId,
                mangaEntry[MangaTable.sourceReference].toString(),

                mangaEntry[MangaTable.url],
                mangaEntry[MangaTable.title],
                proxyThumbnailUrl(mangaId),

                true,

                mangaEntry[MangaTable.artist],
                mangaEntry[MangaTable.author],
                mangaEntry[MangaTable.description],
                mangaEntry[MangaTable.genre],
                MangaStatus.valueOf(mangaEntry[MangaTable.status]).name,
                mangaEntry[MangaTable.inLibrary],
                getSource(mangaEntry[MangaTable.sourceReference]),
                false
            )
        } else { // initialize manga
            val source = getHttpSource(mangaEntry[MangaTable.sourceReference])
            val fetchedManga = source.fetchMangaDetails(
                SManga.create().apply {
                    url = mangaEntry[MangaTable.url]
                    title = mangaEntry[MangaTable.title]
                }
            ).awaitSingle()

            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {

                    it[MangaTable.initialized] = true

                    it[MangaTable.artist] = fetchedManga.artist
                    it[MangaTable.author] = fetchedManga.author
                    it[MangaTable.description] = truncate(fetchedManga.description, 4096)
                    it[MangaTable.genre] = fetchedManga.genre
                    it[MangaTable.status] = fetchedManga.status
                    if (fetchedManga.thumbnail_url != null && fetchedManga.thumbnail_url.orEmpty().isNotEmpty())
                        it[MangaTable.thumbnail_url] = fetchedManga.thumbnail_url
                }
            }

            clearMangaThumbnail(mangaId)

            mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }

            MangaDataClass(
                mangaId,
                mangaEntry[MangaTable.sourceReference].toString(),

                mangaEntry[MangaTable.url],
                mangaEntry[MangaTable.title],
                proxyThumbnailUrl(mangaId),

                true,

                fetchedManga.artist,
                fetchedManga.author,
                fetchedManga.description,
                fetchedManga.genre,
                MangaStatus.valueOf(fetchedManga.status).name,
                false,
                getSource(mangaEntry[MangaTable.sourceReference]),
                true
            )
        }
    }

    private val applicationDirs by DI.global.instance<ApplicationDirs>()
    suspend fun getMangaThumbnail(mangaId: Int): Pair<InputStream, String> {
        val saveDir = applicationDirs.mangaThumbnailsRoot
        val fileName = mangaId.toString()

        return getCachedImageResponse(saveDir, fileName) {
            getManga(mangaId) // make sure is initialized

            val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }

            val sourceId = mangaEntry[MangaTable.sourceReference]
            val source = getHttpSource(sourceId)

            val thumbnailUrl = mangaEntry[MangaTable.thumbnail_url]!!

            source.client.newCall(
                GET(thumbnailUrl, source.headers)
            ).await()
        }
    }

    private fun clearMangaThumbnail(mangaId: Int) {
        val saveDir = applicationDirs.mangaThumbnailsRoot
        val fileName = mangaId.toString()

        clearCachedImage(saveDir, fileName)
    }
}
