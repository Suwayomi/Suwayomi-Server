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
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue

val getMangaUpdateQueue = ArrayBlockingQueue<Pair<Int, SManga?>>(1000)
@Volatile
var getMangaCount = 0

val getMangaUpdateQueueThread = Runnable {
    while (true) {
        val p = getMangaUpdateQueue.take()
        println("took ${p.first}")
        while (getMangaCount > 0) {
            println("count is $getMangaCount")
            Thread.sleep(1000)
        }
        val mangaId = p.first
        println("working on $mangaId")
        val fetchedManga = p.second!!
        try {
            transaction {
                println("transaction start $mangaId")
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
                println("transaction end $mangaId")
            }
        } catch (e: Exception) {
            println(e)
        }
    }
}

fun getManga(mangaId: Int, proxyThumbnail: Boolean = true): MangaDataClass {
    synchronized(getMangaCount) {
        getMangaCount++
    }
    return try {
        transaction {
            var mangaEntry = MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!!

            return@transaction if (mangaEntry[MangaTable.initialized]) {
                println("${mangaEntry[MangaTable.title]} is initialized")
                println("${mangaEntry[MangaTable.thumbnail_url]}")
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

                // update database
                // TODO: sqlite gets fucked here
                println("putting $mangaId")
                getMangaUpdateQueue.put(Pair(mangaId, fetchedManga))

//            mangaEntry = MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!!
                val newThumbnail =
                    if (fetchedManga.thumbnail_url != null && fetchedManga.thumbnail_url!!.isNotEmpty()) {
                        fetchedManga.thumbnail_url
                    } else mangaEntry[MangaTable.thumbnail_url]

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
    } finally {
        synchronized(getMangaCount) {
            getMangaCount--
        }
    }
}

fun getThumbnail(mangaId: Int): Pair<InputStream, String> {
    return transaction {
        var filePath = Config.thumbnailsRoot + "/$mangaId"
        var mangaEntry = MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!!

        val potentialCache = findFileNameStartingWith(Config.thumbnailsRoot, mangaId.toString())
        if (potentialCache != null) {
            println("using cached thumbnail file")
            return@transaction Pair(
                pathToInputStream(potentialCache),
                "image/${potentialCache.substringAfter("$mangaId.")}"
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

        println(response.code)

        if (response.code == 200) {
            val contentType = response.headers["content-type"]!!
            filePath += "." + contentType.substringAfter("image/")

            writeStream(response.body!!.byteStream(), filePath)

            return@transaction Pair(
                pathToInputStream(filePath),
                contentType
            )
        } else {
            throw Exception("request error! ${response.code}")
        }
    }
}
