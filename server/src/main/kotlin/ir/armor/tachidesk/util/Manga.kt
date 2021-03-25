package ir.armor.tachidesk.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import ir.armor.tachidesk.applicationDirs
import ir.armor.tachidesk.database.dataclass.MangaDataClass
import ir.armor.tachidesk.database.table.MangaStatus
import ir.armor.tachidesk.database.table.MangaTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.InputStream

fun getManga(mangaId: Int, proxyThumbnail: Boolean = true): MangaDataClass {
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
        ).toBlocking().first()

        transaction {
            MangaTable.update({ MangaTable.id eq mangaId }) {

                it[MangaTable.initialized] = true

                it[MangaTable.artist] = fetchedManga.artist
                it[MangaTable.author] = fetchedManga.author
                it[MangaTable.description] = fetchedManga.description
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

fun getThumbnail(mangaId: Int): Pair<InputStream, String> {
    val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!! }
    val saveDir = applicationDirs.thumbnailsRoot
    val fileName = mangaId.toString()

    return getCachedResponse(saveDir, fileName) {
        val sourceId = mangaEntry[MangaTable.sourceReference]
        val source = getHttpSource(sourceId)
        var thumbnailUrl = mangaEntry[MangaTable.thumbnail_url]
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
            thumbnailUrl = getManga(mangaId, proxyThumbnail = false).thumbnailUrl!!
        }

        source.client.newCall(
            GET(thumbnailUrl, source.headers)
        ).execute()
    }
}
