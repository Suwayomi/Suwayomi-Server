package ir.armor.tachidesk.util

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import ir.armor.tachidesk.Config
import ir.armor.tachidesk.database.dataclass.MangaDataClass
import ir.armor.tachidesk.database.table.MangaStatus
import ir.armor.tachidesk.database.table.MangaTable
import ir.armor.tachidesk.database.table.SourceTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.io.InputStream

fun getManga(mangaId: Int, proxyThumbnail: Boolean = true): MangaDataClass {
    var mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!! }

    return if (mangaEntry[MangaTable.initialized]) {
        MangaDataClass(
            mangaId,
            mangaEntry[MangaTable.sourceReference].value,

            mangaEntry[MangaTable.url],
            mangaEntry[MangaTable.title],
            if (proxyThumbnail) proxyThumbnailUrl(mangaId) else mangaEntry[MangaTable.thumbnail_url],

            true,

            mangaEntry[MangaTable.artist],
            mangaEntry[MangaTable.author],
            mangaEntry[MangaTable.description],
            mangaEntry[MangaTable.genre],
            MangaStatus.valueOf(mangaEntry[MangaTable.status]).name,
        )
    } else { // initialize manga
        val source = getHttpSource(mangaEntry[MangaTable.sourceReference].value)
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
            mangaEntry[MangaTable.sourceReference].value,

            mangaEntry[MangaTable.url],
            mangaEntry[MangaTable.title],
            if (proxyThumbnail) proxyThumbnailUrl(mangaId) else newThumbnail,

            true,

            fetchedManga.artist,
            fetchedManga.author,
            fetchedManga.description,
            fetchedManga.genre,
            MangaStatus.valueOf(fetchedManga.status).name,
        )
    }
}

fun getThumbnail(mangaId: Int): Pair<InputStream, String> {
    val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!! }
    var filePath = "${Config.thumbnailsRoot}/$mangaId."

    val potentialCache = findFileNameStartingWith(Config.thumbnailsRoot, mangaId.toString())
    if (potentialCache != null) {
        println("using cached thumbnail file")
        return Pair(
            pathToInputStream(potentialCache),
            "image/${potentialCache.substringAfter(filePath)}"
        )
    }

    val sourceId = mangaEntry[MangaTable.sourceReference].value
    println("getting source for $mangaId")
    val source = getHttpSource(sourceId)
    var thumbnailUrl = mangaEntry[MangaTable.thumbnail_url]
    if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
        thumbnailUrl = getManga(mangaId, proxyThumbnail = false).thumbnailUrl!!
    }
    println(thumbnailUrl)
    val response = source.client.newCall(
        GET(thumbnailUrl, source.headers)
    ).execute()

    if (response.code == 200) {
        val contentType = response.headers["content-type"]!!
        filePath += contentType.substringAfter("image/")

        writeStream(response.body!!.byteStream(), filePath)

        return Pair(
            pathToInputStream(filePath),
            contentType
        )
    } else {
        throw Exception("request error! ${response.code}")
    }
}

fun getMangaDir(mangaId: Int): String {
    val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!! }
    val sourceId = mangaEntry[MangaTable.sourceReference].value
    val sourceEntry = transaction { SourceTable.select { SourceTable.id eq sourceId }.firstOrNull()!! }

    val mangaTitle = mangaEntry[MangaTable.title]
    val sourceName = sourceEntry[SourceTable.name]

    val mangaDir = "${Config.mangaRoot}/$sourceName/$mangaTitle"
    // make sure dirs exist
    File(mangaDir).mkdirs()
    return mangaDir
}
