package suwayomi.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jetbrains.exposed.sql.SortOrder.DESC
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.impl.Manga.getManga
import suwayomi.tachidesk.impl.util.GetHttpSource.getHttpSource
import suwayomi.tachidesk.impl.util.lang.awaitSingle
import suwayomi.tachidesk.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.model.table.ChapterTable
import suwayomi.tachidesk.model.table.MangaTable
import suwayomi.tachidesk.model.table.PageTable
import suwayomi.tachidesk.model.table.toDataClass

object Chapter {
    /** get chapter list when showing a manga */
    suspend fun getChapterList(mangaId: Int, onlineFetch: Boolean?): List<ChapterDataClass> {
        return if (onlineFetch == true) {
            getSourceChapters(mangaId)
        } else {
            transaction {
                ChapterTable.select { ChapterTable.manga eq mangaId }.orderBy(ChapterTable.chapterIndex to DESC)
                    .map {
                        ChapterTable.toDataClass(it)
                    }
            }.ifEmpty {
                // If it was explicitly set to offline dont grab chapters
                if (onlineFetch == null) {
                    getSourceChapters(mangaId)
                } else emptyList()
            }
        }
    }

    private suspend fun getSourceChapters(mangaId: Int): List<ChapterDataClass> {
        val mangaDetails = getManga(mangaId)
        val source = getHttpSource(mangaDetails.sourceId.toLong())
        val chapterList = source.fetchChapterList(
            SManga.create().apply {
                title = mangaDetails.title
                url = mangaDetails.url
            }
        ).awaitSingle()

        val chapterCount = chapterList.count()

        transaction {
            chapterList.reversed().forEachIndexed { index, fetchedChapter ->
                val chapterEntry = ChapterTable.select { ChapterTable.url eq fetchedChapter.url }.firstOrNull()
                if (chapterEntry == null) {
                    ChapterTable.insert {
                        it[url] = fetchedChapter.url
                        it[name] = fetchedChapter.name
                        it[date_upload] = fetchedChapter.date_upload
                        it[chapter_number] = fetchedChapter.chapter_number
                        it[scanlator] = fetchedChapter.scanlator

                        it[chapterIndex] = index + 1
                        it[manga] = mangaId
                    }
                } else {
                    ChapterTable.update({ ChapterTable.url eq fetchedChapter.url }) {
                        it[name] = fetchedChapter.name
                        it[date_upload] = fetchedChapter.date_upload
                        it[chapter_number] = fetchedChapter.chapter_number
                        it[scanlator] = fetchedChapter.scanlator

                        it[chapterIndex] = index + 1
                        it[manga] = mangaId
                    }
                }
            }
        }

        // clear any orphaned chapters that are in the db but not in `chapterList`
        val dbChapterCount = transaction { ChapterTable.select { ChapterTable.manga eq mangaId }.count() }
        if (dbChapterCount > chapterCount) { // we got some clean up due
            val dbChapterList = transaction { ChapterTable.select { ChapterTable.manga eq mangaId }.toList() }

            dbChapterList.forEach {
                if (it[ChapterTable.chapterIndex] >= chapterList.size ||
                    chapterList[it[ChapterTable.chapterIndex] - 1].url != it[ChapterTable.url]
                ) {
                    transaction {
                        PageTable.deleteWhere { PageTable.chapter eq it[ChapterTable.id] }
                        ChapterTable.deleteWhere { ChapterTable.id eq it[ChapterTable.id] }
                    }
                }
            }
        }

        val dbChapterMap = transaction {
            ChapterTable.select { ChapterTable.manga eq mangaId }
                .associateBy({ it[ChapterTable.url] }, { it })
        }

        return chapterList.mapIndexed { index, it ->

            val dbChapter = dbChapterMap.getValue(it.url)

            ChapterDataClass(
                it.url,
                it.name,
                it.date_upload,
                it.chapter_number,
                it.scanlator,
                mangaId,

                dbChapter[ChapterTable.isRead],
                dbChapter[ChapterTable.isBookmarked],
                dbChapter[ChapterTable.lastPageRead],
                dbChapter[ChapterTable.lastPageReadOffset],

                chapterCount - index,
                chapterList.size
            )
        }
    }

    /** used to display a chapter, get a chapter in order to show it's pages */
    suspend fun getChapter(chapterIndex: Int, mangaId: Int): ChapterDataClass {
        val chapterEntry = transaction {
            ChapterTable.select {
                (ChapterTable.chapterIndex eq chapterIndex) and (ChapterTable.manga eq mangaId)
            }.first()
        }
        val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }
        val source = getHttpSource(mangaEntry[MangaTable.sourceReference])

        val pageList = source.fetchPageList(
            SChapter.create().apply {
                url = chapterEntry[ChapterTable.url]
                name = chapterEntry[ChapterTable.name]
            }
        ).awaitSingle()

        val chapterId = chapterEntry[ChapterTable.id].value
        val chapterCount = transaction { ChapterTable.select { ChapterTable.manga eq mangaId }.count() }

        // update page list for this chapter
        transaction {
            pageList.forEach { page ->
                val pageEntry = transaction { PageTable.select { (PageTable.chapter eq chapterId) and (PageTable.index eq page.index) }.firstOrNull() }
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

        return ChapterDataClass(
            chapterEntry[ChapterTable.url],
            chapterEntry[ChapterTable.name],
            chapterEntry[ChapterTable.date_upload],
            chapterEntry[ChapterTable.chapter_number],
            chapterEntry[ChapterTable.scanlator],
            mangaId,
            chapterEntry[ChapterTable.isRead],
            chapterEntry[ChapterTable.isBookmarked],
            chapterEntry[ChapterTable.lastPageRead],
            chapterEntry[ChapterTable.lastPageReadOffset],

            chapterEntry[ChapterTable.chapterIndex],
            chapterCount.toInt(),
            pageList.count()
        )
    }

    fun modifyChapter(mangaId: Int, chapterIndex: Int, isRead: Boolean?, isBookmarked: Boolean?, markPrevRead: Boolean?, lastPageRead: Int?, lastPageReadOffset: Int?) {
        transaction {
            if (listOf(isRead, isBookmarked, lastPageRead).any { it != null }) {
                ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.chapterIndex eq chapterIndex) }) { update ->
                    isRead?.also {
                        update[ChapterTable.isRead] = it
                    }
                    isBookmarked?.also {
                        update[ChapterTable.isBookmarked] = it
                    }
                    lastPageRead?.also {
                        update[ChapterTable.lastPageRead] = it
                    }
                    lastPageReadOffset?.also {
                        update[ChapterTable.lastPageReadOffset] = it
                    }
                }
            }

            markPrevRead?.let {
                ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.chapterIndex less chapterIndex) }) {
                    it[ChapterTable.isRead] = markPrevRead
                }
            }
        }
    }
}
