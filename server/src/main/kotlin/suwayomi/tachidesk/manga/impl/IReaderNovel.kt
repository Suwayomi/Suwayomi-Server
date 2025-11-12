package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import ireader.core.source.CatalogSource
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import kotlinx.coroutines.runBlocking

object IReaderNovel {
    private val logger = KotlinLogging.logger {}

    fun getPopularNovels(
        sourceId: Long,
        page: Int,
    ): MangasPageInfo =
        runBlocking {
            val source =
                IReaderSource.getCatalogueSourceOrNull(sourceId) as? CatalogSource
                    ?: throw Exception("Source not found or not a CatalogSource")

            val listings = source.getListings()
            val listing = listings.firstOrNull()

            source.getMangaList(listing, page)
        }

    fun getLatestNovels(
        sourceId: Long,
        page: Int,
    ): MangasPageInfo =
        runBlocking {
            val source =
                IReaderSource.getCatalogueSourceOrNull(sourceId) as? CatalogSource
                    ?: throw Exception("Source not found or not a CatalogSource")

            val listings = source.getListings()
            val listing = listings.getOrNull(1) ?: listings.firstOrNull()

            source.getMangaList(listing, page)
        }

    fun searchNovels(
        sourceId: Long,
        query: String,
        page: Int,
    ): MangasPageInfo =
        runBlocking {
            val source =
                IReaderSource.getCatalogueSourceOrNull(sourceId) as? CatalogSource
                    ?: throw Exception("Source not found or not a CatalogSource")

            val filters: FilterList = listOf(Filter.Title(query))
            source.getMangaList(filters, page)
        }

    fun getNovelDetails(
        sourceId: Long,
        novelUrl: String,
    ): MangaInfo =
        runBlocking {
            val source =
                IReaderSource.getCatalogueSourceOrNull(sourceId)
                    ?: throw Exception("Source not found")

            val manga = MangaInfo(key = novelUrl, title = "")
            source.getMangaDetails(manga, emptyList())
        }

    fun getChapterList(
        sourceId: Long,
        novelUrl: String,
    ): List<ChapterInfo> =
        runBlocking {
            val source =
                IReaderSource.getCatalogueSourceOrNull(sourceId)
                    ?: throw Exception("Source not found")

            val manga = MangaInfo(key = novelUrl, title = "")
            source.getChapterList(manga, emptyList())
        }

    fun getChapterContent(
        sourceId: Long,
        chapterUrl: String,
    ): List<Page> =
        runBlocking {
            val source =
                IReaderSource.getCatalogueSourceOrNull(sourceId)
                    ?: throw Exception("Source not found")

            val chapter = ChapterInfo(key = chapterUrl, name = "")
            source.getPageList(chapter, emptyList())
        }
}
