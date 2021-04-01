package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.awaitSingle
import ir.armor.tachidesk.impl.Manga.getManga
import ir.armor.tachidesk.impl.Source.getHttpSource
import ir.armor.tachidesk.model.database.ChapterTable
import ir.armor.tachidesk.model.database.MangaTable
import ir.armor.tachidesk.model.database.PageTable
import ir.armor.tachidesk.model.dataclass.ChapterDataClass
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object Chapter {
    suspend fun getChapterList(mangaId: Int): List<ChapterDataClass> {
        val mangaDetails = getManga(mangaId)
        val source = getHttpSource(mangaDetails.sourceId.toLong())

        val chapterList = source.fetchChapterList(
            SManga.create().apply {
                title = mangaDetails.title
                url = mangaDetails.url
            }
        ).awaitSingle()

        val chapterCount = chapterList.count()

        return transaction {
            chapterList.reversed().forEachIndexed { index, fetchedChapter ->
                val chapterEntry = ChapterTable.select { ChapterTable.url eq fetchedChapter.url }.firstOrNull()
                if (chapterEntry == null) {
                    ChapterTable.insertAndGetId {
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

            // clear any orphaned chapters
            val dbChapterCount = transaction { ChapterTable.selectAll().count() }
            if (dbChapterCount > chapterCount) { // we got some clean up due
                // TODO: delete orphan chapters
            }

            chapterList.mapIndexed { index, it ->
                ChapterDataClass(
                    ChapterTable.select { ChapterTable.url eq it.url }.firstOrNull()!![ChapterTable.id].value,
                    it.url,
                    it.name,
                    it.date_upload,
                    it.chapter_number,
                    it.scanlator,
                    mangaId,
                    chapterCount - index,
                    chapterCount
                )
            }
        }
    }

    suspend fun getChapter(chapterIndex: Int, mangaId: Int): ChapterDataClass {
        var chapterEntry: ResultRow? = null
        var source: HttpSource? = null
        var sChapter: SChapter? = null
        transaction {
            chapterEntry = ChapterTable.select {
                ChapterTable.chapterIndex eq chapterIndex and (ChapterTable.manga eq mangaId)
            }.firstOrNull()!!
            val mangaEntry = MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!!
            source = getHttpSource(mangaEntry[MangaTable.sourceReference])
            sChapter = SChapter.create().apply {
                url = chapterEntry!![ChapterTable.url]
                name = chapterEntry!![ChapterTable.name]
            }
        }
        val pageList = source!!.fetchPageList(
            sChapter!!
        ).awaitSingle()

        return transaction {
            val chapterRow = chapterEntry!!

            val chapterId = chapterRow[ChapterTable.id].value
            val chapterCount = transaction { ChapterTable.selectAll().count() }

            val chapter = ChapterDataClass(
                chapterId,
                chapterRow[ChapterTable.url],
                chapterRow[ChapterTable.name],
                chapterRow[ChapterTable.date_upload],
                chapterRow[ChapterTable.chapter_number],
                chapterRow[ChapterTable.scanlator],
                mangaId,
                chapterRow[ChapterTable.chapterIndex],
                chapterCount.toInt(),

                pageList.count()
            )

            pageList.forEach { page ->
                val pageEntry = transaction { PageTable.select { (PageTable.chapter eq chapterId) and (PageTable.index eq page.index) }.firstOrNull() }
                if (pageEntry == null) {
                    transaction {
                        PageTable.insert {
                            it[index] = page.index
                            it[url] = page.url
                            it[imageUrl] = page.imageUrl
                            it[this.chapter] = chapterId
                        }
                    }
                } else {
                    transaction {
                        PageTable.update({ (PageTable.chapter eq chapterId) and (PageTable.index eq page.index) }) {
                            it[url] = page.url
                            it[imageUrl] = page.imageUrl
                        }
                    }
                }
            }

            chapter
        }
    }
}
