package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
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
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.clearCachedImage
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.impl.util.updateMangaDownloadDir
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import java.io.File
import java.io.IOException
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
                mangaEntry[MangaTable.inLibraryAt],
                getSource(mangaEntry[MangaTable.sourceReference]),
                getMangaMetaMap(mangaId),
                mangaEntry[MangaTable.realUrl],
                false
            )
        } else { // initialize manga
            val source = getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])
            val sManga = SManga.create().apply {
                url = mangaEntry[MangaTable.url]
                title = mangaEntry[MangaTable.title]
            }
            val networkManga = source.fetchMangaDetails(sManga).awaitSingle()
            sManga.copyFrom(networkManga)

            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {

                    if (sManga.title != mangaEntry[MangaTable.title]) {
                        val canUpdateTitle = updateMangaDownloadDir(mangaId, sManga.title)

                        if (canUpdateTitle)
                            it[MangaTable.title] = sManga.title
                    }
                    it[MangaTable.initialized] = true

                    it[MangaTable.artist] = sManga.artist
                    it[MangaTable.author] = sManga.author
                    it[MangaTable.description] = truncate(sManga.description, 4096)
                    it[MangaTable.genre] = sManga.genre
                    it[MangaTable.status] = sManga.status
                    if (sManga.thumbnail_url != null && sManga.thumbnail_url.orEmpty().isNotEmpty())
                        it[MangaTable.thumbnail_url] = sManga.thumbnail_url

                    it[MangaTable.realUrl] = runCatching {
                        (source as? HttpSource)?.mangaDetailsRequest(sManga)?.url?.toString()
                    }.getOrNull()
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

                sManga.artist,
                sManga.author,
                sManga.description,
                sManga.genre.toGenreList(),
                MangaStatus.valueOf(sManga.status).name,
                mangaEntry[MangaTable.inLibrary],
                mangaEntry[MangaTable.inLibraryAt],
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
            val manga = MangaTable.select { MangaTable.id eq mangaId }
                .first()[MangaTable.id]
            val meta = transaction {
                MangaMetaTable.select { (MangaMetaTable.ref eq manga) and (MangaMetaTable.key eq key) }
            }.firstOrNull()

            if (meta == null) {
                MangaMetaTable.insert {
                    it[MangaMetaTable.key] = key
                    it[MangaMetaTable.value] = value
                    it[MangaMetaTable.ref] = manga
                }
            } else {
                MangaMetaTable.update({ (MangaMetaTable.ref eq manga) and (MangaMetaTable.key eq key) }) {
                    it[MangaMetaTable.value] = value
                }
            }
        }
    }

    private val applicationDirs by DI.global.instance<ApplicationDirs>()
    suspend fun getMangaThumbnail(mangaId: Int, useCache: Boolean): Pair<InputStream, String> {
        val saveDir = applicationDirs.thumbnailsRoot
        val fileName = mangaId.toString()

        val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }
        val sourceId = mangaEntry[MangaTable.sourceReference]

        return when (val source = getCatalogueSourceOrStub(sourceId)) {
            is HttpSource -> getImageResponse(saveDir, fileName, useCache) {
                val thumbnailUrl = mangaEntry[MangaTable.thumbnail_url]
                    ?: if (!mangaEntry[MangaTable.initialized]) {
                        // initialize then try again
                        getManga(mangaId)
                        transaction {
                            MangaTable.select { MangaTable.id eq mangaId }.first()
                        }[MangaTable.thumbnail_url]!!
                    } else {
                        // source provides no thumbnail url for this manga
                        throw NullPointerException()
                    }

                source.client.newCall(
                    GET(thumbnailUrl, source.headers)
                ).await()
            }
            is LocalSource -> {
                val imageFile = mangaEntry[MangaTable.thumbnail_url]?.let {
                    val file = File(it)
                    if (file.exists()) {
                        file
                    } else {
                        null
                    }
                } ?: throw IOException("Thumbnail does not exist")
                val contentType = ImageUtil.findImageType { imageFile.inputStream() }?.mime
                    ?: "image/jpeg"
                imageFile.inputStream() to contentType
            }
            else -> throw IllegalArgumentException("Unknown source")
        }
    }

    private fun clearMangaThumbnail(mangaId: Int) {
        val saveDir = applicationDirs.thumbnailsRoot
        val fileName = mangaId.toString()

        clearCachedImage(saveDir, fileName)
    }
}
