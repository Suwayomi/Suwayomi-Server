package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SortOrder.ASC
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Manga.getManga
import suwayomi.tachidesk.manga.impl.util.getChapterDir
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.PaginatedList
import suwayomi.tachidesk.manga.model.dataclass.paginatedFrom
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import java.io.File
import java.time.Instant

object Chapter {
    /** get chapter list when showing a manga */
    suspend fun getChapterList(mangaId: Int, onlineFetch: Boolean = false): List<ChapterDataClass> {
        return if (onlineFetch) {
            getSourceChapters(mangaId)
        } else {
            transaction {
                ChapterTable.select { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                    .map {
                        ChapterTable.toDataClass(it)
                    }
            }.ifEmpty {
                getSourceChapters(mangaId)
            }
        }
    }

    private suspend fun getSourceChapters(mangaId: Int): List<ChapterDataClass> {
        val manga = getManga(mangaId)
        val source = getCatalogueSourceOrStub(manga.sourceId.toLong())

        val sManga = SManga.create().apply {
            title = manga.title
            url = manga.url
        }

        val chapterList = source.fetchChapterList(sManga).awaitSingle()

        // Recognize number for new chapters.
        chapterList.forEach {
            (source as? HttpSource)?.prepareNewChapter(it, sManga)
            ChapterRecognition.parseChapterNumber(it, sManga)
        }

        val chapterCount = chapterList.count()
        var now = Instant.now().epochSecond

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

                        it[sourceOrder] = index + 1
                        it[fetchedAt] = now++
                        it[ChapterTable.manga] = mangaId
                    }
                } else {
                    ChapterTable.update({ ChapterTable.url eq fetchedChapter.url }) {
                        it[name] = fetchedChapter.name
                        it[date_upload] = fetchedChapter.date_upload
                        it[chapter_number] = fetchedChapter.chapter_number
                        it[scanlator] = fetchedChapter.scanlator

                        it[sourceOrder] = index + 1
                        it[ChapterTable.manga] = mangaId
                    }
                }
            }
        }

        // clear any orphaned/duplicate chapters that are in the db but not in `chapterList`
        val dbChapterCount = transaction { ChapterTable.select { ChapterTable.manga eq mangaId }.count() }
        if (dbChapterCount > chapterCount) { // we got some clean up due
            val dbChapterList = transaction {
                ChapterTable.select { ChapterTable.manga eq mangaId }.orderBy(ChapterTable.url to ASC).toList()
            }
            val chapterUrls = chapterList.map { it.url }.toSet()

            dbChapterList.forEachIndexed { index, dbChapter ->
                if (
                    !chapterUrls.contains(dbChapter[ChapterTable.url]) || // is orphaned
                    (index < dbChapterList.lastIndex && dbChapter[ChapterTable.url] == dbChapterList[index + 1][ChapterTable.url]) // is duplicate
                ) {
                    transaction {
                        PageTable.deleteWhere { PageTable.chapter eq dbChapter[ChapterTable.id] }
                        ChapterTable.deleteWhere { ChapterTable.id eq dbChapter[ChapterTable.id] }
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
                dbChapter[ChapterTable.lastReadAt],

                chapterCount - index,
                dbChapter[ChapterTable.fetchedAt],
                dbChapter[ChapterTable.isDownloaded],

                dbChapter[ChapterTable.pageCount],

                chapterList.size,
                meta = getChapterMetaMap(dbChapter[ChapterTable.id])
            )
        }
    }

    fun modifyChapter(
        mangaId: Int,
        chapterIndex: Int,
        isRead: Boolean?,
        isBookmarked: Boolean?,
        markPrevRead: Boolean?,
        lastPageRead: Int?
    ) {
        transaction {
            if (listOf(isRead, isBookmarked, lastPageRead).any { it != null }) {
                ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }) { update ->
                    isRead?.also {
                        update[ChapterTable.isRead] = it
                    }
                    isBookmarked?.also {
                        update[ChapterTable.isBookmarked] = it
                    }
                    lastPageRead?.also {
                        update[ChapterTable.lastPageRead] = it
                        update[ChapterTable.lastReadAt] = Instant.now().epochSecond
                    }
                }
            }

            markPrevRead?.let {
                ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder less chapterIndex) }) {
                    it[ChapterTable.isRead] = markPrevRead
                }
            }
        }
    }

    fun getChapterMetaMap(chapter: EntityID<Int>): Map<String, String> {
        return transaction {
            ChapterMetaTable.select { ChapterMetaTable.ref eq chapter }
                .associate { it[ChapterMetaTable.key] to it[ChapterMetaTable.value] }
        }
    }

    fun modifyChapterMeta(mangaId: Int, chapterIndex: Int, key: String, value: String) {
        transaction {
            val chapterId =
                ChapterTable.select { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                    .first()[ChapterTable.id].value
            val meta = transaction {
                ChapterMetaTable.select { (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }
            }.firstOrNull()

            if (meta == null) {
                ChapterMetaTable.insert {
                    it[ChapterMetaTable.key] = key
                    it[ChapterMetaTable.value] = value
                    it[ChapterMetaTable.ref] = chapterId
                }
            } else {
                ChapterMetaTable.update({ (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }) {
                    it[ChapterMetaTable.value] = value
                }
            }
        }
    }

    fun deleteChapter(mangaId: Int, chapterIndex: Int) {
        transaction {
            val chapterId =
                ChapterTable.select { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                    .first()[ChapterTable.id].value

            val chapterDir = getChapterDir(mangaId, chapterId)

            File(chapterDir).deleteRecursively()

            ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }) {
                it[isDownloaded] = false
            }
        }
    }

    fun getRecentChapters(pageNum: Int): PaginatedList<MangaChapterDataClass> {
        return paginatedFrom(pageNum) {
            transaction {
                (ChapterTable innerJoin MangaTable)
                    .select { (MangaTable.inLibrary eq true) and (ChapterTable.fetchedAt greater MangaTable.inLibraryAt) }
                    .orderBy(ChapterTable.fetchedAt to SortOrder.DESC)
                    .map {
                        MangaChapterDataClass(
                            MangaTable.toDataClass(it),
                            ChapterTable.toDataClass(it)
                        )
                    }
            }
        }
    }
}
