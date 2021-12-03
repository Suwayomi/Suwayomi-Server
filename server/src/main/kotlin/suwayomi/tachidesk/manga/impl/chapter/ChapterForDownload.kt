package suwayomi.tachidesk.manga.impl.chapter

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Page.getPageName
import suwayomi.tachidesk.manga.impl.util.getChapterDir
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.manga.model.table.toDataClass

suspend fun getChapterDownloadReady(chapterIndex: Int, mangaId: Int): ChapterDataClass {
    val chapter = ChapterForDownload(chapterIndex, mangaId)

    return chapter.asDownloadReady()
}

private class ChapterForDownload(
    private val chapterIndex: Int,
    private val mangaId: Int
) {
    suspend fun asDownloadReady(): ChapterDataClass {

        if (isNotCompletelyDownloaded()) {
            markAsNotDownloaded()

            val pageList = fetchPageList()

            updateDatabasePages(pageList)
        }

        return asDataClass()
    }

    private fun asDataClass() = ChapterTable.toDataClass(chapterEntry)

    var chapterEntry: ResultRow = freshChapterEntry()

    private fun freshChapterEntry() = transaction {
        ChapterTable.select {
            (ChapterTable.sourceOrder eq chapterIndex) and (ChapterTable.manga eq mangaId)
        }.first()
    }

    private suspend fun fetchPageList(): List<Page> {
        val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }
        val source = getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])

        return source.fetchPageList(
            SChapter.create().apply {
                url = chapterEntry[ChapterTable.url]
                name = chapterEntry[ChapterTable.name]
            }
        ).awaitSingle()
    }

    private fun markAsNotDownloaded() {
        // chapter may be downloaded but if we are here, then images might be deleted and database data be false
        transaction {
            ChapterTable.update({ (ChapterTable.sourceOrder eq chapterIndex) and (ChapterTable.manga eq mangaId) }) {
                it[isDownloaded] = false
            }
        }
    }

    private fun updateDatabasePages(pageList: List<Page>) {
        val chapterId = chapterEntry[ChapterTable.id].value
        val chapterIndex = chapterEntry[ChapterTable.sourceOrder]
        val mangaId = chapterEntry[ChapterTable.manga].value

        transaction {
            pageList.forEach { page ->
                val pageEntry = transaction {
                    PageTable.select { (PageTable.chapter eq chapterId) and (PageTable.index eq page.index) }
                        .firstOrNull()
                }
                if (pageEntry == null) {
                    PageTable.insert {
                        it[index] = page.index
                        it[url] = page.url
                        it[imageUrl] = page.imageUrl
                        it[chapter] = chapterId
                    }
                } else {
                    PageTable.update({ (PageTable.chapter eq chapterId) and (PageTable.index eq page.index) }) {
                        it[url] = page.url
                        it[imageUrl] = page.imageUrl
                    }
                }
            }
        }

        updatePageCount(pageList, mangaId, chapterIndex)

        // chapter was updated
        chapterEntry = freshChapterEntry()
    }

    private fun updatePageCount(
        pageList: List<Page>,
        mangaId: Int,
        chapterIndex: Int
    ) {
        val pageCount = pageList.count()

        transaction {
            ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }) {
                it[ChapterTable.pageCount] = pageCount
            }
        }
    }

    private fun isNotCompletelyDownloaded(): Boolean {
        return !(chapterEntry[ChapterTable.isDownloaded] && firstPageExists())
    }

    private fun firstPageExists(): Boolean {
        val chapterId = chapterEntry[ChapterTable.id].value

        val chapterDir = getChapterDir(mangaId, chapterId)

        println(chapterDir)
        println(getPageName(0))

        return ImageResponse.findFileNameStartingWith(
            chapterDir,
            getPageName(0)
        ) != null
    }
}
