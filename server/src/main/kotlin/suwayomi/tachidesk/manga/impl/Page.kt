package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.util.GetHttpSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.impl.util.getChapterDir
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.server.ApplicationDirs
import java.io.File
import java.io.InputStream

object Page {
    /**
     * A page might have a imageUrl ready from the get go, or we might need to
     * go an extra step and call fetchImageUrl to get it.
     */
    suspend fun getTrueImageUrl(page: Page, source: HttpSource): String {
        if (page.imageUrl == null) {
            page.imageUrl = source.fetchImageUrl(page).awaitSingle()
        }
        return page.imageUrl!!
    }

    suspend fun getPageImage(mangaId: Int, chapterIndex: Int, index: Int, useCache: Boolean = true): Pair<InputStream, String> {
        val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }
        val source = getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])
        val chapterEntry = transaction {
            ChapterTable.select {
                (ChapterTable.sourceOrder eq chapterIndex) and (ChapterTable.manga eq mangaId)
            }.first()
        }
        val chapterId = chapterEntry[ChapterTable.id].value

        val pageEntry =
            transaction { PageTable.select { (PageTable.chapter eq chapterId) and (PageTable.index eq index) }.first() }

        val tachiyomiPage = Page(
            pageEntry[PageTable.index],
            pageEntry[PageTable.url],
            pageEntry[PageTable.imageUrl]
        )

        // we treat Local source differently
        if (source.id == LocalSource.ID) {
            // is of archive format
            if (LocalSource.pageCache.containsKey(chapterEntry[ChapterTable.url])) {
                val pageStream = LocalSource.pageCache[chapterEntry[ChapterTable.url]]!![index]
                return pageStream() to (ImageUtil.findImageType { pageStream() }?.mime ?: "image/jpeg")
            }

            // is of directory format
            val imageFile = File(tachiyomiPage.imageUrl!!)
            return imageFile.inputStream() to (ImageUtil.findImageType { imageFile.inputStream() }?.mime ?: "image/jpeg")
        }

        source as HttpSource

        if (pageEntry[PageTable.imageUrl] == null) {
            val trueImageUrl = getTrueImageUrl(tachiyomiPage, source)
            transaction {
                PageTable.update({ (PageTable.chapter eq chapterId) and (PageTable.index eq index) }) {
                    it[imageUrl] = trueImageUrl
                }
            }
        }

        val chapterDir = getChapterDir(mangaId, chapterId)
        File(chapterDir).mkdirs()
        val fileName = getPageName(index, chapterDir) // e.g. 001

        return getImageResponse(chapterDir, fileName, useCache) {
            source.fetchImage(tachiyomiPage).awaitSingle()
        }
    }

    // TODO(v0.6.0) : zero based pages are deprecated
    fun getPageName(index: Int, chapterDir: String): String {
        val zeroBasedPageExists = ImageResponse.findFileNameStartingWith(
            chapterDir,
            formatPageName(0)
        ) != null

        if (zeroBasedPageExists) return formatPageName(index)

        return formatPageName(index + 1)
    }

    private fun formatPageName(index: Int) = String.format("%03d", index)

    private val applicationDirs by DI.global.instance<ApplicationDirs>()
}
