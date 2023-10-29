package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.Manga.getMangaMetaMap
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.PagedMangaListDataClass
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable

object MangaList {
    fun proxyThumbnailUrl(mangaId: Int): String {
        return "/api/v1/manga/$mangaId/thumbnail"
    }

    suspend fun getMangaList(
        sourceId: Long,
        pageNum: Int = 1,
        popular: Boolean,
    ): PagedMangaListDataClass {
        require(pageNum > 0) {
            "pageNum = $pageNum is not in valid range"
        }
        val source = getCatalogueSourceOrStub(sourceId)
        val mangasPage =
            if (popular) {
                source.getPopularManga(pageNum)
            } else {
                if (source.supportsLatest) {
                    source.getLatestUpdates(pageNum)
                } else {
                    throw Exception("Source $source doesn't support latest")
                }
            }
        return mangasPage.processEntries(sourceId)
    }

    fun MangasPage.insertOrGet(sourceId: Long): List<Int> {
        return transaction {
            mangas.map { manga ->
                val mangaEntry =
                    MangaTable.select {
                        (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq sourceId)
                    }.firstOrNull()
                if (mangaEntry == null) { // create manga entry
                    val mangaId =
                        MangaTable.insertAndGetId {
                            it[url] = manga.url
                            it[title] = manga.title

                            it[artist] = manga.artist
                            it[author] = manga.author
                            it[description] = manga.description
                            it[genre] = manga.genre
                            it[status] = manga.status
                            it[thumbnail_url] = manga.thumbnail_url
                            it[updateStrategy] = manga.update_strategy.name

                            it[sourceReference] = sourceId
                        }.value

                    // delete thumbnail in case cached data still exists
                    Manga.clearThumbnail(mangaId)

                    mangaId
                } else {
                    mangaEntry[MangaTable.id].value
                }
            }
        }
    }

    fun MangasPage.processEntries(sourceId: Long): PagedMangaListDataClass {
        val mangasPage = this
        val mangaList =
            transaction {
                return@transaction mangasPage.mangas.map { manga ->
                    var mangaEntry =
                        MangaTable.select {
                            (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq sourceId)
                        }.firstOrNull()
                    if (mangaEntry == null) { // create manga entry
                        val mangaId =
                            MangaTable.insertAndGetId {
                                it[url] = manga.url
                                it[title] = manga.title

                                it[artist] = manga.artist
                                it[author] = manga.author
                                it[description] = manga.description
                                it[genre] = manga.genre
                                it[status] = manga.status
                                it[thumbnail_url] = manga.thumbnail_url
                                it[updateStrategy] = manga.update_strategy.name

                                it[sourceReference] = sourceId
                            }.value

                        // delete thumbnail in case cached data still exists
                        Manga.clearThumbnail(mangaId)

                        mangaEntry =
                            MangaTable.select {
                                (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq sourceId)
                            }.first()

                        MangaDataClass(
                            id = mangaId,
                            sourceId = sourceId.toString(),
                            url = manga.url,
                            title = manga.title,
                            thumbnailUrl = proxyThumbnailUrl(mangaId),
                            thumbnailUrlLastFetched = mangaEntry[MangaTable.thumbnailUrlLastFetched],
                            initialized = manga.initialized,
                            artist = manga.artist,
                            author = manga.author,
                            description = manga.description,
                            genre = manga.genre.toGenreList(),
                            status = MangaStatus.valueOf(manga.status).name,
                            inLibrary = false, // It's a new manga entry
                            inLibraryAt = 0,
                            meta = getMangaMetaMap(mangaId),
                            realUrl = mangaEntry[MangaTable.realUrl],
                            lastFetchedAt = mangaEntry[MangaTable.lastFetchedAt],
                            chaptersLastFetchedAt = mangaEntry[MangaTable.chaptersLastFetchedAt],
                            updateStrategy = UpdateStrategy.valueOf(mangaEntry[MangaTable.updateStrategy]),
                            freshData = true,
                        )
                    } else {
                        val mangaId = mangaEntry[MangaTable.id].value
                        MangaDataClass(
                            id = mangaId,
                            sourceId = sourceId.toString(),
                            url = manga.url,
                            title = manga.title,
                            thumbnailUrl = proxyThumbnailUrl(mangaId),
                            thumbnailUrlLastFetched = mangaEntry[MangaTable.thumbnailUrlLastFetched],
                            initialized = true,
                            artist = mangaEntry[MangaTable.artist],
                            author = mangaEntry[MangaTable.author],
                            description = mangaEntry[MangaTable.description],
                            genre = mangaEntry[MangaTable.genre].toGenreList(),
                            status = MangaStatus.valueOf(mangaEntry[MangaTable.status]).name,
                            inLibrary = mangaEntry[MangaTable.inLibrary],
                            inLibraryAt = mangaEntry[MangaTable.inLibraryAt],
                            meta = getMangaMetaMap(mangaId),
                            realUrl = mangaEntry[MangaTable.realUrl],
                            lastFetchedAt = mangaEntry[MangaTable.lastFetchedAt],
                            chaptersLastFetchedAt = mangaEntry[MangaTable.chaptersLastFetchedAt],
                            updateStrategy = UpdateStrategy.valueOf(mangaEntry[MangaTable.updateStrategy]),
                            freshData = false,
                        )
                    }
                }
            }
        return PagedMangaListDataClass(
            mangaList,
            mangasPage.hasNextPage,
        )
    }
}
