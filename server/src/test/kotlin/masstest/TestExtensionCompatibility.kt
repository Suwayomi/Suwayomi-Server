package masstest

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.os.Looper
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.stopKoin
import suwayomi.tachidesk.manga.impl.Source.getSourceList
import suwayomi.tachidesk.manga.impl.extension.Extension.installExtension
import suwayomi.tachidesk.manga.impl.extension.Extension.uninstallExtension
import suwayomi.tachidesk.manga.impl.extension.Extension.updateExtension
import suwayomi.tachidesk.manga.impl.extension.ExtensionStoreService
import suwayomi.tachidesk.manga.impl.extension.ExtensionsList.getExtensionList
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrNull
import suwayomi.tachidesk.manga.model.dataclass.ExtensionDataClass
import suwayomi.tachidesk.server.applicationSetup
import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.test.BASE_PATH
import suwayomi.tachidesk.test.setLoggingEnabled
import xyz.nulldev.ts.config.CONFIG_PREFIX
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestExtensionCompatibility {
    private val logger = KotlinLogging.logger {}
    private lateinit var extensions: List<ExtensionDataClass>
    private lateinit var sources: List<HttpSource>

    private val mangaToFetch = mutableListOf<Pair<HttpSource, SManga>>()
    private val failedToFetch = mutableListOf<Pair<HttpSource, Exception>>()
    private val mangaFailedToFetch = mutableListOf<Triple<HttpSource, SManga, Exception>>()
    private val chaptersToFetch = mutableListOf<Triple<HttpSource, SManga, SChapter>>()
    private val chaptersPageListFailedToFetch = mutableListOf<Triple<HttpSource, Pair<SManga, SChapter>, Exception>>()

    val repos = listOf<String>()

    @BeforeAll
    fun setup() {
        val dataRoot = File(BASE_PATH).absolutePath
        System.setProperty("$CONFIG_PREFIX.server.rootDir", dataRoot)
        Looper.clearMainLooperForTest()
        SettingsRegistry.clear()
        applicationSetup()
        repos.forEach {
            val store = runBlocking {
                ExtensionStoreService.fetch(it)
            }
            ExtensionStoreService.upsert(store)
        }
        ExtensionStoreService.syncDbToPrefs()
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
                        try {
                            uninstallExtension(it.pkgName)
                        } catch (_: Exception) {}
                        installExtension(it.pkgName)
                    }
                }
            }
            sources =
                getSourceList()
                    .filter {
                        // filter local source
                        it.id.toLong() != 0L
                    }.map { getSourceOrNull(it.id.toLong())!! as HttpSource }
        }
        setLoggingEnabled(true)
        File("$BASE_PATH/sources.txt").writeText(sources.joinToString("\n") { "${it.name} - ${it.lang.uppercase()} - ${it.id}" })
    }

    @AfterAll
    fun teardown() {
        stopKoin()
    }

    @Test
    fun runTest() {
        runBlocking(Dispatchers.Default) {
            val semaphore = Semaphore(10)
            val popularCount = AtomicInteger(1)
            sources
                .map { source ->
                    async {
                        semaphore.withPermit {
                            logger.info { "${popularCount.getAndIncrement()} - Now fetching popular manga from $source" }
                            try {
                                mangaToFetch += source to (
                                    repeat { source.getPopularManga(1) }
                                        .mangas
                                        .firstOrNull()
                                        ?: throw Exception("Source returned no manga")
                                )
                            } catch (e: Exception) {
                                logger.warn { "Failed to fetch popular manga from $source: ${e.message}" }
                                failedToFetch += source to e
                            }
                        }
                    }
                }.awaitAll()
            File("$BASE_PATH/failedToFetch.txt").writeText(
                failedToFetch.joinToString("\n") { (source, exception) ->
                    "${source.name} (${source.lang.uppercase()}, ${source.id}):" +
                        " ${exception.message}"
                },
            )
            logger.info { "Now fetching manga info from ${mangaToFetch.size} sources" }

            val mangaCount = AtomicInteger(1)
            mangaToFetch
                .map { (source, manga) ->
                    async {
                        semaphore.withPermit {
                            logger.info { "${mangaCount.getAndIncrement()} - Now fetching manga from $source" }
                            try {
                                repeat { source.getMangaUpdate(manga, emptyList(), true, true) }
                            } catch (e: Exception) {
                                logger.warn {
                                    "Failed to fetch manga info and chapters from $source for ${manga.title} (${source.getMangaUrl(
                                        manga,
                                    )}): ${e.message}"
                                }
                                mangaFailedToFetch += Triple(source, manga, e)
                            }
                        }
                    }
                }.awaitAll()
            File("$BASE_PATH/MangaFailedToFetch.txt").writeText(
                mangaFailedToFetch.joinToString("\n") { (source, manga, exception) ->
                    "${source.name} (${source.lang}, ${source.id}):" +
                        " ${manga.title} (${source.getMangaUrl(manga)}):" +
                        " ${exception.message}"
                },
            )
            logger.info { "Now fetching manga chapters from ${mangaToFetch.size} sources" }

            val pageListCount = AtomicInteger(1)
            chaptersToFetch
                .map { (source, manga, chapter) ->
                    async {
                        semaphore.withPermit {
                            logger.info { "${pageListCount.getAndIncrement()} - Now fetching page list from $source" }
                            try {
                                repeat { source.getPageList(chapter) }
                            } catch (e: Exception) {
                                logger.warn {
                                    "Failed to fetch manga info from $source for ${manga.title} (${source.getMangaUrl(
                                        manga,
                                    )}): ${e.message}"
                                }
                                chaptersPageListFailedToFetch += Triple(source, manga to chapter, e)
                            }
                        }
                    }
                }.awaitAll()

            File("$BASE_PATH/ChapterPageListFailedToFetch.txt").writeText(
                chaptersPageListFailedToFetch.joinToString("\n") { (source, manga, exception) ->
                    "${source.name} (${source.lang}, ${source.id}):" +
                        " ${manga.first.title} (${source.getMangaUrl(manga.first)}):" +
                        " ${manga.second.name} (${manga.second.url}): ${exception.message}"
                },
            )
        }
    }

    private suspend fun <T> repeat(block: suspend () -> T): T {
        for (i in 1..2) {
            try {
                return block()
            } catch (e: Exception) {
            }
        }
        return block()
    }
}
