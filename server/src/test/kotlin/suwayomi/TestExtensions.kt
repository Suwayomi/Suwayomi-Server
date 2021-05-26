package suwayomi

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mu.KotlinLogging
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import rx.Observable
import suwayomi.server.applicationSetup
import suwayomi.tachidesk.impl.Source.getSourceList
import suwayomi.tachidesk.impl.extension.Extension.installExtension
import suwayomi.tachidesk.impl.extension.Extension.uninstallExtension
import suwayomi.tachidesk.impl.extension.Extension.updateExtension
import suwayomi.tachidesk.impl.extension.ExtensionsList.getExtensionList
import suwayomi.tachidesk.impl.util.GetHttpSource.getHttpSource
import suwayomi.tachidesk.impl.util.lang.awaitSingle
import suwayomi.tachidesk.model.dataclass.ExtensionDataClass
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestExtensions {
    private val logger = KotlinLogging.logger {}
    private lateinit var extensions: List<ExtensionDataClass>
    private lateinit var sources: List<HttpSource>

    private val mangaToFetch = mutableListOf<Pair<HttpSource, SManga>>()
    private val failedToFetch = mutableListOf<Pair<HttpSource, Exception>>()
    private val mangaFailedToFetch = mutableListOf<Triple<HttpSource, SManga, Exception>>()
    private val chaptersToFetch = mutableListOf<Triple<HttpSource, SManga, SChapter>>()
    private val chaptersFailedToFetch = mutableListOf<Triple<HttpSource, SManga, Throwable>>()
    private val chaptersPageListFailedToFetch = mutableListOf<Triple<HttpSource, Pair<SManga, SChapter>, Exception>>()

    @BeforeAll
    fun setup() {
        val dataRoot = File("tmp/TestDesk").absolutePath
        System.setProperty("ir.armor.tachidesk.rootDir", dataRoot)
        applicationSetup()
        setLoggingEnabled(false)

        runBlocking {
            extensions = getExtensionList()
            extensions.forEach {
                when {
                    it.obsolete -> {
                        uninstallExtension(it.pkgName)
                    }
                    it.hasUpdate -> {
                        updateExtension(it.pkgName)
                    }
                    else -> {
                        installExtension(it.pkgName)
                    }
                }
            }
            sources = getSourceList().map { getHttpSource(it.id.toLong()) }
        }
        setLoggingEnabled(true)
        File("tmp/TestDesk/sources.txt").writeText(sources.joinToString("\n") { "${it.name} - ${it.lang.toUpperCase()} - ${it.id}" })
    }

    @Test
    fun runTest() {
        runBlocking(Dispatchers.Default) {
            val semaphore = Semaphore(10)
            sources.mapIndexed { index, source ->
                async {
                    semaphore.withPermit {
                        logger.info { "$index - Now fetching popular manga from $source" }
                        try {
                            mangaToFetch += source to (
                                source.fetchPopularManga(1)
                                    .awaitSingleRepeat().mangas.firstOrNull()
                                    ?: throw Exception("Source returned no manga")
                                )
                        } catch (e: Exception) {
                            logger.warn { "Failed to fetch popular manga from $source: ${e.message}" }
                            failedToFetch += source to e
                        }
                    }
                }
            }.awaitAll()
            File("tmp/TestDesk/failedToFetch.txt").writeText(
                failedToFetch.joinToString("\n") { (source, exception) ->
                    "${source.name} (${source.lang.toUpperCase()}, ${source.id}):" +
                        " ${exception.message}"
                }
            )
            logger.info { "Now fetching manga info from ${mangaToFetch.size} sources" }

            mangaToFetch.mapIndexed { index, (source, manga) ->
                async {
                    semaphore.withPermit {
                        logger.info { "$index - Now fetching manga from $source" }
                        try {
                            manga.copyFrom(source.fetchMangaDetails(manga).awaitSingleRepeat())
                            manga.initialized = true
                        } catch (e: Exception) {
                            logger.warn {
                                "Failed to fetch manga info from $source for ${manga.title} (${source.mangaDetailsRequest(manga).url}): ${e.message}"
                            }
                            mangaFailedToFetch += Triple(source, manga, e)
                        }
                    }
                }
            }.awaitAll()
            File("tmp/TestDesk/MangaFailedToFetch.txt").writeText(
                mangaFailedToFetch.joinToString("\n") { (source, manga, exception) ->
                    "${source.name} (${source.lang}, ${source.id}):" +
                        " ${manga.title} (${source.mangaDetailsRequest(manga).url}):" +
                        " ${exception.message}"
                }
            )
            logger.info { "Now fetching manga chapters from ${mangaToFetch.size} sources" }

            mangaToFetch.filter { it.second.initialized }.mapIndexed { index, (source, manga) ->
                async {
                    semaphore.withPermit {
                        logger.info { "$index - Now fetching manga chapters from $source" }
                        try {
                            chaptersToFetch += Triple(
                                source,
                                manga,
                                source.fetchChapterList(manga).awaitSingleRepeat().firstOrNull() ?: throw Exception("Source returned no chapters")
                            )
                        } catch (e: Exception) {
                            logger.warn {
                                "Failed to fetch manga chapters from $source for ${manga.title} (${source.mangaDetailsRequest(manga).url}): ${e.message}"
                            }
                            chaptersFailedToFetch += Triple(source, manga, e)
                        } catch (e: NoClassDefFoundError) {
                            logger.warn {
                                "Failed to fetch manga chapters from $source for ${manga.title} (${source.mangaDetailsRequest(manga).url}): ${e.message}"
                            }
                            chaptersFailedToFetch += Triple(source, manga, e)
                        }
                    }
                }
            }.awaitAll()

            File("tmp/TestDesk/ChaptersFailedToFetch.txt").writeText(
                chaptersFailedToFetch.joinToString("\n") { (source, manga, exception) ->
                    "${source.name} (${source.lang}, ${source.id}):" +
                        " ${manga.title} (${source.mangaDetailsRequest(manga).url}):" +
                        " ${exception.message}"
                }
            )

            chaptersToFetch.mapIndexed { index, (source, manga, chapter) ->
                async {
                    semaphore.withPermit {
                        logger.info { "$index - Now fetching page list from $source" }
                        try {
                            source.fetchPageList(chapter).awaitSingleRepeat()
                        } catch (e: Exception) {
                            logger.warn {
                                "Failed to fetch manga info from $source for ${manga.title} (${source.mangaDetailsRequest(manga).url}): ${e.message}"
                            }
                            chaptersPageListFailedToFetch += Triple(source, manga to chapter, e)
                        }
                    }
                }
            }.awaitAll()

            File("tmp/TestDesk/ChapterPageListFailedToFetch.txt").writeText(
                chaptersPageListFailedToFetch.joinToString("\n") { (source, manga, exception) ->
                    "${source.name} (${source.lang}, ${source.id}):" +
                        " ${manga.first.title} (${source.mangaDetailsRequest(manga.first).url}):" +
                        " ${manga.second.name} (${manga.second.url}): ${exception.message}"
                }
            )
        }
    }

    private suspend fun <T> Observable<T>.awaitSingleRepeat(): T {
        for (i in 1..2) {
            try {
                return awaitSingle()
            } catch (e: Exception) {}
        }
        return awaitSingle()
    }
}
