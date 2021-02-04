package ir.armor.tachidesk.util

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.database.table.ChapterTable
import ir.armor.tachidesk.database.table.MangaTable
import ir.armor.tachidesk.database.table.PageTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

fun getTrueImageUrl(page: Page, source: HttpSource): String {
    if (page.imageUrl == null) {
        page.imageUrl = source.fetchImageUrl(page).toBlocking().first()!!
    }
    return page.imageUrl!!
}

fun getPageImage(mangaId: Int, chapterId: Int, index: Int): Pair<InputStream, String> {
    val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!! }
    val source = getHttpSource(mangaEntry[MangaTable.sourceReference].value)
    val chapterEntry = transaction { ChapterTable.select { ChapterTable.id eq chapterId }.firstOrNull()!! }
    val pageEntry = transaction { PageTable.select { (PageTable.chapter eq chapterId) and (PageTable.index eq index) }.firstOrNull()!! }

    val tachiPage = Page(
        pageEntry[PageTable.index],
        pageEntry[PageTable.url],
        pageEntry[PageTable.imageUrl]
    )

    if (pageEntry[PageTable.imageUrl] == null) {
        transaction {
            PageTable.update({ (PageTable.chapter eq chapterId) and (PageTable.index eq index) }) {
                it[imageUrl] = getTrueImageUrl(tachiPage, source)
            }
        }
    }

    val saveDir = getMangaDir(mangaId) + "/" + chapterEntry[ChapterTable.chapter_number]
    File(saveDir).mkdirs()
    var filePath = "$saveDir/$index."

    val potentialCache = findFileNameStartingWith(saveDir, index.toString())
    if (potentialCache != null) {
        println("using cached page file for $index")
        return Pair(
            pathToInputStream(potentialCache),
            "image/${potentialCache.substringAfter("$filePath")}"
        )
    }

    val response = source.fetchImage(tachiPage).toBlocking().first()

    if (response.code == 200) {
        val contentType = response.headers["content-type"]!!
        filePath += contentType.substringAfter("image/")

        Files.newOutputStream(Paths.get(filePath)).use { os ->

            response.body!!.source().saveTo(os)
        }

//        writeStream(response.body!!.source(), filePath)

        return Pair(
            pathToInputStream(filePath),
            contentType
        )
    } else {
        throw Exception("request error! ${response.code}")
    }
}
