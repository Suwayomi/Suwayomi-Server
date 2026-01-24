package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import ireader.core.source.Source
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.Page

/**
 * Wrapper class that implements CatalogSource by delegating to a source instance
 * loaded from a different classloader using reflection.
 *
 * This wrapper allows the server to interact with IReader sources that are loaded
 * from external JARs with different classloaders.
 */
class IReaderSourceWrapper(
    private val sourceInstance: Any,
) : ireader.core.source.CatalogSource {
    private val logger = KotlinLogging.logger {}

    override val id: Long by lazy {
        try {
            val method = sourceInstance.javaClass.getMethod("getId")
            method.invoke(sourceInstance) as Long
        } catch (e: Exception) {
            logger.error(e) { "Failed to get source ID" }
            -1L
        }
    }

    override val name: String by lazy {
        try {
            val method = sourceInstance.javaClass.getMethod("getName")
            method.invoke(sourceInstance) as String
        } catch (e: Exception) {
            logger.error(e) { "Failed to get source name" }
            "Unknown"
        }
    }

    override val lang: String by lazy {
        try {
            val method = sourceInstance.javaClass.getMethod("getLang")
            method.invoke(sourceInstance) as String
        } catch (e: Exception) {
            logger.error(e) { "Failed to get source lang" }
            "en"
        }
    }

    override suspend fun getMangaDetails(
        manga: MangaInfo,
        commands: List<Command<*>>,
    ): MangaInfo =
        try {
            val source = sourceInstance as ireader.core.source.Source
            source.getMangaDetails(manga, commands)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get manga details" }
            manga
        }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>,
    ): List<ChapterInfo> =
        try {
            val source = sourceInstance as ireader.core.source.Source
            source.getChapterList(manga, commands)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get chapter list" }
            emptyList()
        }

    override suspend fun getPageList(
        chapter: ChapterInfo,
        commands: List<Command<*>>,
    ): List<Page> =
        try {
            val source = sourceInstance as ireader.core.source.Source
            source.getPageList(chapter, commands)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get page list" }
            emptyList()
        }

    override fun getRegex(): Regex =
        try {
            val source = sourceInstance as ireader.core.source.Source
            source.getRegex()
        } catch (e: Exception) {
            Regex("")
        }

    override suspend fun getMangaList(
        sort: ireader.core.source.model.Listing?,
        page: Int,
    ): ireader.core.source.model.MangasPageInfo =
        try {
            // Since the source is loaded with server's classloader as parent, we can cast directly
            val catalogSource = sourceInstance as ireader.core.source.CatalogSource
            catalogSource.getMangaList(sort, page)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get manga list" }
            ireader.core.source.model
                .MangasPageInfo(emptyList(), false)
        }

    override suspend fun getMangaList(
        filters: ireader.core.source.model.FilterList,
        page: Int,
    ): ireader.core.source.model.MangasPageInfo =
        try {
            // Since the source is loaded with server's classloader as parent, we can cast directly
            val catalogSource = sourceInstance as ireader.core.source.CatalogSource
            catalogSource.getMangaList(filters, page)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get manga list with filters" }
            ireader.core.source.model
                .MangasPageInfo(emptyList(), false)
        }

    override fun getListings(): List<ireader.core.source.model.Listing> =
        try {
            val catalogSource = sourceInstance as ireader.core.source.CatalogSource
            catalogSource.getListings()
        } catch (e: Exception) {
            logger.error(e) { "Failed to get listings" }
            emptyList()
        }

    override fun getFilters(): ireader.core.source.model.FilterList =
        try {
            val catalogSource = sourceInstance as ireader.core.source.CatalogSource
            catalogSource.getFilters()
        } catch (e: Exception) {
            logger.error(e) { "Failed to get filters" }
            emptyList()
        }

    override fun getCommands(): ireader.core.source.model.CommandList =
        try {
            val catalogSource = sourceInstance as ireader.core.source.CatalogSource
            catalogSource.getCommands()
        } catch (e: Exception) {
            emptyList()
        }

    override fun toString(): String = name
}
