package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.impl.util.GetHttpSource.getHttpSource
import ir.armor.tachidesk.impl.util.awaitSingle
import ir.armor.tachidesk.impl.util.storage.CachedImageResponse.getCachedImageResponse
import ir.armor.tachidesk.impl.util.storage.SafePath
import ir.armor.tachidesk.model.database.table.ChapterTable
import ir.armor.tachidesk.model.database.table.MangaTable
import ir.armor.tachidesk.model.database.table.PageTable
import ir.armor.tachidesk.server.ApplicationDirs
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
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

    suspend fun getPageImage(mangaId: Int, chapterIndex: Int, index: Int): Pair<InputStream, String> {
        val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }
        val source = getHttpSource(mangaEntry[MangaTable.sourceReference])
        val chapterEntry = transaction {
            ChapterTable.select {
                (ChapterTable.chapterIndex eq chapterIndex) and (ChapterTable.manga eq mangaId)
            }.first()
        }
        val chapterId = chapterEntry[ChapterTable.id].value

        val pageEntry = transaction { PageTable.select { (PageTable.chapter eq chapterId) and (PageTable.index eq index) }.first() }

        val tachiPage = Page(
            pageEntry[PageTable.index],
            pageEntry[PageTable.url],
            pageEntry[PageTable.imageUrl]
        )

        if (pageEntry[PageTable.imageUrl] == null) {
            val trueImageUrl = getTrueImageUrl(tachiPage, source)
            transaction {
                PageTable.update({ (PageTable.chapter eq chapterId) and (PageTable.index eq index) }) {
                    it[imageUrl] = trueImageUrl
                }
            }
        }

        val saveDir = getChapterDir(mangaId, chapterId)
        File(saveDir).mkdirs()
        val fileName = String.format("%03d", index) // e.g. 001.jpeg

        return getCachedImageResponse(saveDir, fileName) {
            source.fetchImage(tachiPage).awaitSingle()
        }
    }

    private val applicationDirs by DI.global.instance<ApplicationDirs>()
    private fun getChapterDir(mangaId: Int, chapterId: Int): String {
        val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }
        val source = getHttpSource(mangaEntry[MangaTable.sourceReference])
        val chapterEntry = transaction { ChapterTable.select { ChapterTable.id eq chapterId }.first() }

        val sourceDir = source.toString()
        val mangaDir = SafePath.buildValidFilename(mangaEntry[MangaTable.title])
        val chapterDir = SafePath.buildValidFilename(
            when {
                chapterEntry[ChapterTable.scanlator] != null -> "${chapterEntry[ChapterTable.scanlator]}_${chapterEntry[ChapterTable.name]}"
                else -> chapterEntry[ChapterTable.name]
            }
        )

        return "${applicationDirs.mangaRoot}/$sourceDir/$mangaDir/$chapterDir"
    }
}
