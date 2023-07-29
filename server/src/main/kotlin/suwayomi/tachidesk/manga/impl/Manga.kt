package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
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
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrNull
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.clearCachedImage
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getCachedImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.impl.util.updateMangaDownloadDir
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant

object Manga {
    private fun truncate(text: String?, maxLength: Int): String? {
        return if (text?.length ?: 0 > maxLength) {
            text?.take(maxLength - 3) + "..."
        } else {
            text
        }
    }

    suspend fun getManga(userId: Int, mangaId: Int, onlineFetch: Boolean = false): MangaDataClass {
        var mangaEntry = transaction { MangaTable.getWithUserData(userId).select { MangaTable.id eq mangaId }.first() }

        return if (!onlineFetch && mangaEntry[MangaTable.initialized]) {
            getMangaDataClass(userId, mangaId, mangaEntry)
        } else { // initialize manga
            val sManga = fetchManga(mangaId) ?: return getMangaDataClass(userId, mangaId, mangaEntry)

            mangaEntry = transaction { MangaTable.getWithUserData(userId).select { MangaTable.id eq mangaId }.first() }

            MangaDataClass(
                id = mangaId,
                sourceId = mangaEntry[MangaTable.sourceReference].toString(),

                url = mangaEntry[MangaTable.url],
                title = mangaEntry[MangaTable.title],
                thumbnailUrl = proxyThumbnailUrl(mangaId),
                thumbnailUrlLastFetched = mangaEntry[MangaTable.thumbnailUrlLastFetched],

                initialized = true,

                artist = sManga.artist,
                author = sManga.author,
                description = sManga.description,
                genre = sManga.genre.toGenreList(),
                status = MangaStatus.valueOf(sManga.status).name,
                inLibrary = mangaEntry.getOrNull(MangaUserTable.inLibrary) ?: false,
                inLibraryAt = mangaEntry.getOrNull(MangaUserTable.inLibraryAt) ?: 0,
                source = getSource(mangaEntry[MangaTable.sourceReference]),
                meta = getMangaMetaMap(userId, mangaId),
                realUrl = mangaEntry[MangaTable.realUrl],
                lastFetchedAt = mangaEntry[MangaTable.lastFetchedAt],
                chaptersLastFetchedAt = mangaEntry[MangaTable.chaptersLastFetchedAt],
                updateStrategy = UpdateStrategy.valueOf(mangaEntry[MangaTable.updateStrategy]),
                freshData = true
            )
        }
    }

    suspend fun fetchManga(mangaId: Int): SManga? {
        val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }

        val source = getCatalogueSourceOrNull(mangaEntry[MangaTable.sourceReference])
            ?: return null
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

                    if (canUpdateTitle) {
                        it[MangaTable.title] = sManga.title
                    }
                }
                it[MangaTable.initialized] = true

                it[MangaTable.artist] = sManga.artist
                it[MangaTable.author] = sManga.author
                it[MangaTable.description] = truncate(sManga.description, 4096)
                it[MangaTable.genre] = sManga.genre
                it[MangaTable.status] = sManga.status
                if (!sManga.thumbnail_url.isNullOrEmpty() && sManga.thumbnail_url != mangaEntry[MangaTable.thumbnail_url]) {
                    it[MangaTable.thumbnail_url] = sManga.thumbnail_url
                    it[MangaTable.thumbnailUrlLastFetched] = Instant.now().epochSecond
                    clearMangaThumbnailCache(mangaId)
                }

                it[MangaTable.realUrl] = runCatching {
                    (source as? HttpSource)?.getMangaUrl(sManga)
                }.getOrNull()

                it[MangaTable.lastFetchedAt] = Instant.now().epochSecond

                it[MangaTable.updateStrategy] = sManga.update_strategy.name
            }
        }

        return sManga
    }

    suspend fun getMangaFull(userId: Int, mangaId: Int, onlineFetch: Boolean = false): MangaDataClass {
        val mangaDaaClass = getManga(userId, mangaId, onlineFetch)

        return transaction {
            val unreadCount =
                ChapterTable
                    .getWithUserData(userId)
                    .select { (ChapterTable.manga eq mangaId) and (ChapterUserTable.isRead eq false) }
                    .count()

            val downloadCount =
                ChapterTable
                    .select { (ChapterTable.manga eq mangaId) and (ChapterTable.isDownloaded eq true) }
                    .count()

            val chapterCount =
                ChapterTable
                    .select { (ChapterTable.manga eq mangaId) }
                    .count()

            val lastChapterRead =
                ChapterTable.getWithUserData(userId)
                    .select { (ChapterTable.manga eq mangaId) }
                    .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                    .firstOrNull { it.getOrNull(ChapterUserTable.isRead) == true }

            mangaDaaClass.unreadCount = unreadCount
            mangaDaaClass.downloadCount = downloadCount
            mangaDaaClass.chapterCount = chapterCount
            mangaDaaClass.lastChapterRead = lastChapterRead?.let { ChapterTable.toDataClass(userId, it) }

            mangaDaaClass
        }
    }

    private fun getMangaDataClass(userId: Int, mangaId: Int, mangaEntry: ResultRow) = MangaDataClass(
        id = mangaId,
        sourceId = mangaEntry[MangaTable.sourceReference].toString(),

        url = mangaEntry[MangaTable.url],
        title = mangaEntry[MangaTable.title],
        thumbnailUrl = proxyThumbnailUrl(mangaId),
        thumbnailUrlLastFetched = mangaEntry[MangaTable.thumbnailUrlLastFetched],

        initialized = true,

        artist = mangaEntry[MangaTable.artist],
        author = mangaEntry[MangaTable.author],
        description = mangaEntry[MangaTable.description],
        genre = mangaEntry[MangaTable.genre].toGenreList(),
        status = MangaStatus.valueOf(mangaEntry[MangaTable.status]).name,
        inLibrary = mangaEntry.getOrNull(MangaUserTable.inLibrary) ?: false,
        inLibraryAt = mangaEntry.getOrNull(MangaUserTable.inLibraryAt) ?: 0,
        source = getSource(mangaEntry[MangaTable.sourceReference]),
        meta = getMangaMetaMap(userId, mangaId),
        realUrl = mangaEntry[MangaTable.realUrl],
        lastFetchedAt = mangaEntry[MangaTable.lastFetchedAt],
        chaptersLastFetchedAt = mangaEntry[MangaTable.chaptersLastFetchedAt],
        updateStrategy = UpdateStrategy.valueOf(mangaEntry[MangaTable.updateStrategy]),
        freshData = false
    )

    fun getMangaMetaMap(userId: Int, mangaId: Int): Map<String, String> {
        return transaction {
            MangaMetaTable.select { MangaMetaTable.user eq userId and (MangaMetaTable.ref eq mangaId) }
                .associate { it[MangaMetaTable.key] to it[MangaMetaTable.value] }
        }
    }

    fun modifyMangaMeta(userId: Int, mangaId: Int, key: String, value: String) {
        transaction {
            val meta =
                MangaMetaTable.select {
                    MangaMetaTable.user eq userId and
                        (MangaMetaTable.ref eq mangaId) and
                        (MangaMetaTable.key eq key)
                }.firstOrNull()

            if (meta == null) {
                MangaMetaTable.insert {
                    it[MangaMetaTable.key] = key
                    it[MangaMetaTable.value] = value
                    it[MangaMetaTable.ref] = mangaId
                    it[MangaMetaTable.user] = userId
                }
            } else {
                MangaMetaTable.update(
                    {
                        MangaMetaTable.user eq userId and
                            (MangaMetaTable.ref eq mangaId) and
                            (MangaMetaTable.key eq key)
                    }
                ) {
                    it[MangaMetaTable.value] = value
                }
            }
        }
    }

    private val applicationDirs by DI.global.instance<ApplicationDirs>()
    private val network: NetworkHelper by injectLazy()
    suspend fun getMangaThumbnail(mangaId: Int): Pair<InputStream, String> {
        val cacheSaveDir = applicationDirs.thumbnailsRoot
        val fileName = mangaId.toString()

        val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }
        val sourceId = mangaEntry[MangaTable.sourceReference]

        return when (val source = getCatalogueSourceOrStub(sourceId)) {
            is HttpSource -> getCachedImageResponse(cacheSaveDir, fileName) {
                val thumbnailUrl = mangaEntry[MangaTable.thumbnail_url]
                    ?: if (!mangaEntry[MangaTable.initialized]) {
                        // initialize then try again
                        // no need for a user id since we are just fetching if required
                        getManga(1, mangaId)
                        transaction {
                            MangaTable.select { MangaTable.id eq mangaId }.first()
                        }[MangaTable.thumbnail_url]!!
                    } else {
                        // source provides no thumbnail url for this manga
                        throw NullPointerException("No thumbnail found")
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

            is StubSource -> getCachedImageResponse(cacheSaveDir, fileName) {
                val thumbnailUrl = mangaEntry[MangaTable.thumbnail_url]
                    ?: throw NullPointerException("No thumbnail found")
                network.client.newCall(
                    GET(thumbnailUrl)
                ).await()
            }

            else -> throw IllegalArgumentException("Unknown source")
        }
    }

    private fun clearMangaThumbnailCache(mangaId: Int) {
        val saveDir = applicationDirs.thumbnailsRoot
        val fileName = mangaId.toString()

        clearCachedImage(saveDir, fileName)
    }
}
