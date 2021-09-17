package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.impl.Source.getSource
import suwayomi.tachidesk.manga.impl.util.GetHttpSource.getHttpSource
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.storage.CachedImageResponse.clearCachedImage
import suwayomi.tachidesk.manga.impl.util.storage.CachedImageResponse.getCachedImageResponse
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
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
                mangaEntry[MangaTable.genre].toGenreList(),
                MangaStatus.valueOf(mangaEntry[MangaTable.status]).name,
                mangaEntry[MangaTable.inLibrary],
                getSource(mangaEntry[MangaTable.sourceReference]),
                getMangaMetaMap(mangaId),
                mangaEntry[MangaTable.realUrl],
                false
            )
        } else { // initialize manga
            val source = getHttpSource(mangaEntry[MangaTable.sourceReference])
            val sManga = SManga.create().apply {
                url = mangaEntry[MangaTable.url]
                title = mangaEntry[MangaTable.title]
            }
            val fetchedManga = source.fetchMangaDetails(sManga).awaitSingle()

            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[MangaTable.title] = fetchedManga.title
                    it[MangaTable.initialized] = true

                    it[MangaTable.artist] = fetchedManga.artist
                    it[MangaTable.author] = fetchedManga.author
                    it[MangaTable.description] = truncate(fetchedManga.description, 4096)
                    it[MangaTable.genre] = fetchedManga.genre
                    it[MangaTable.status] = fetchedManga.status
                    if (fetchedManga.thumbnail_url != null && fetchedManga.thumbnail_url.orEmpty().isNotEmpty())
                        it[MangaTable.thumbnail_url] = fetchedManga.thumbnail_url

                    it[MangaTable.realUrl] = try {
                        source.mangaDetailsRequest(sManga).url.toString()
                    } catch (e: Exception) {
                        null
                    }
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
                fetchedManga.genre.toGenreList(),
                MangaStatus.valueOf(fetchedManga.status).name,
                mangaEntry[MangaTable.inLibrary],
                getSource(mangaEntry[MangaTable.sourceReference]),
                getMangaMetaMap(mangaId),
                mangaEntry[MangaTable.realUrl],
                true
            )
        }
    }

    fun getMangaMetaMap(manga: Int): Map<String, String> {
        return transaction {
            MangaMetaTable.select { MangaMetaTable.ref eq manga }
                .associate { it[MangaMetaTable.key] to it[MangaMetaTable.value] }
        }
    }

    fun modifyMangaMeta(mangaId: Int, key: String, value: String) {
        transaction {
            val manga = MangaMetaTable.select { (MangaTable.id eq mangaId) }
                .first()[MangaTable.id]
            val meta =
                transaction { MangaMetaTable.select { (MangaMetaTable.ref eq manga) and (MangaMetaTable.key eq key) } }.firstOrNull()
            if (meta == null) {
                MangaMetaTable.insert {
                    it[MangaMetaTable.key] = key
                    it[MangaMetaTable.value] = value
                    it[MangaMetaTable.ref] = manga
                }
            } else {
                MangaMetaTable.update {
                    it[MangaMetaTable.value] = value
                }
            }
        }
    }

    private val applicationDirs by DI.global.instance<ApplicationDirs>()
    suspend fun getMangaThumbnail(mangaId: Int): Pair<InputStream, String> {
        val saveDir = applicationDirs.mangaThumbnailsRoot
        val fileName = mangaId.toString()

        return getCachedImageResponse(saveDir, fileName) {
            val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }

            val sourceId = mangaEntry[MangaTable.sourceReference]
            val source = getHttpSource(sourceId)

            val thumbnailUrl: String = mangaEntry[MangaTable.thumbnail_url]
                ?: if (!mangaEntry[MangaTable.initialized]) {
                    // initialize then try again
                    getManga(mangaId)
                    transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }[MangaTable.thumbnail_url]!!
                } else {
                    // source provides no thumbnail url for this manga
                    throw NullPointerException()
                }

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
