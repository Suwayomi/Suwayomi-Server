package suwayomi.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.MangasPage
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.impl.util.GetHttpSource.getHttpSource
import suwayomi.tachidesk.impl.util.lang.awaitSingle
import suwayomi.tachidesk.model.database.table.MangaStatus
import suwayomi.tachidesk.model.database.table.MangaTable
import suwayomi.tachidesk.model.dataclass.MangaDataClass
import suwayomi.tachidesk.model.dataclass.PagedMangaListDataClass

object MangaList {
    fun proxyThumbnailUrl(mangaId: Int): String {
        return "/api/v1/manga/$mangaId/thumbnail"
    }

    suspend fun getMangaList(sourceId: Long, pageNum: Int = 1, popular: Boolean): PagedMangaListDataClass {
        val source = getHttpSource(sourceId)
        val mangasPage = if (popular) {
            source.fetchPopularManga(pageNum).awaitSingle()
        } else {
            if (source.supportsLatest)
                source.fetchLatestUpdates(pageNum).awaitSingle()
            else
                throw Exception("Source $source doesn't support latest")
        }
        return mangasPage.processEntries(sourceId)
    }

    fun MangasPage.processEntries(sourceId: Long): PagedMangaListDataClass {
        val mangasPage = this
        val mangaList = transaction {
            return@transaction mangasPage.mangas.map { manga ->
                val mangaEntry = MangaTable.select { MangaTable.url eq manga.url }.firstOrNull()
                if (mangaEntry == null) { // create manga entry
                    val mangaId = MangaTable.insertAndGetId {
                        it[url] = manga.url
                        it[title] = manga.title

                        it[artist] = manga.artist
                        it[author] = manga.author
                        it[description] = manga.description
                        it[genre] = manga.genre
                        it[status] = manga.status
                        it[thumbnail_url] = manga.thumbnail_url

                        it[sourceReference] = sourceId
                    }.value

                    MangaDataClass(
                        mangaId,
                        sourceId.toString(),

                        manga.url,
                        manga.title,
                        proxyThumbnailUrl(mangaId),

                        manga.initialized,

                        manga.artist,
                        manga.author,
                        manga.description,
                        manga.genre,
                        MangaStatus.valueOf(manga.status).name
                    )
                } else {
                    val mangaId = mangaEntry[MangaTable.id].value
                    MangaDataClass(
                        mangaId,
                        sourceId.toString(),

                        manga.url,
                        manga.title,
                        proxyThumbnailUrl(mangaId),

                        true,

                        mangaEntry[MangaTable.artist],
                        mangaEntry[MangaTable.author],
                        mangaEntry[MangaTable.description],
                        mangaEntry[MangaTable.genre],
                        MangaStatus.valueOf(mangaEntry[MangaTable.status]).name,
                        mangaEntry[MangaTable.inLibrary]
                    )
                }
            }
        }
        return PagedMangaListDataClass(
            mangaList,
            mangasPage.hasNextPage
        )
    }
}
