package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import ir.armor.tachidesk.impl.MangaList.proxyThumbnailUrl
import ir.armor.tachidesk.impl.Source.getSource
import ir.armor.tachidesk.impl.util.CachedImageResponse.getCachedImageResponse
import ir.armor.tachidesk.impl.util.GetHttpSource.getHttpSource
import ir.armor.tachidesk.impl.util.await
import ir.armor.tachidesk.impl.util.awaitSingle
import ir.armor.tachidesk.model.database.MangaStatus
import ir.armor.tachidesk.model.database.MangaTable
import ir.armor.tachidesk.model.dataclass.MangaDataClass
import ir.armor.tachidesk.server.ApplicationDirs
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import java.io.InputStream

object Manga {
    private fun truncate(text: String?, maxLength: Int): String? {
        return if (text?.length ?: 0 > maxLength)
            text?.take(maxLength - 3) + "..."
        else
            text
    }

    suspend fun getManga(mangaId: Int, proxyThumbnail: Boolean = true): MangaDataClass {
        var mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!! }

        return if (mangaEntry[MangaTable.initialized]) {
            MangaDataClass(
                mangaId,
                mangaEntry[MangaTable.sourceReference].toString(),

                mangaEntry[MangaTable.url],
                mangaEntry[MangaTable.title],
                if (proxyThumbnail) proxyThumbnailUrl(mangaId) else mangaEntry[MangaTable.thumbnail_url],

                true,

                mangaEntry[MangaTable.artist],
                mangaEntry[MangaTable.author],
                mangaEntry[MangaTable.description],
                mangaEntry[MangaTable.genre],
                MangaStatus.valueOf(mangaEntry[MangaTable.status]).name,
                mangaEntry[MangaTable.inLibrary],
                getSource(mangaEntry[MangaTable.sourceReference])
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
                    if (fetchedManga.thumbnail_url != null && fetchedManga.thumbnail_url!!.isNotEmpty())
                        it[MangaTable.thumbnail_url] = fetchedManga.thumbnail_url
                }
            }

            mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!! }
            val newThumbnail = mangaEntry[MangaTable.thumbnail_url]

            MangaDataClass(
                mangaId,
                mangaEntry[MangaTable.sourceReference].toString(),

                mangaEntry[MangaTable.url],
                mangaEntry[MangaTable.title],
                if (proxyThumbnail) proxyThumbnailUrl(mangaId) else newThumbnail,

                true,

                fetchedManga.artist,
                fetchedManga.author,
                fetchedManga.description,
                fetchedManga.genre,
                MangaStatus.valueOf(fetchedManga.status).name,
                false,
                getSource(mangaEntry[MangaTable.sourceReference])
            )
        }
    }

    private val applicationDirs by DI.global.instance<ApplicationDirs>()
    suspend fun getMangaThumbnail(mangaId: Int): Pair<InputStream, String> {
        val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!! }
        val saveDir = applicationDirs.thumbnailsRoot
        val fileName = mangaId.toString()

        return getCachedImageResponse(saveDir, fileName) {
            val sourceId = mangaEntry[MangaTable.sourceReference]
            val source = getHttpSource(sourceId)
            var thumbnailUrl = mangaEntry[MangaTable.thumbnail_url]
            if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
                thumbnailUrl = getManga(mangaId, proxyThumbnail = false).thumbnailUrl!!
            }

            source.client.newCall(
                GET(thumbnailUrl, source.headers)
            ).await()
        }
    }
}
