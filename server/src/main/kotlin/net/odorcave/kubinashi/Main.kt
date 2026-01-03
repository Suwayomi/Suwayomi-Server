package net.odorcave.kubinashi

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.google.errorprone.annotations.Immutable
import com.google.gson.LongSerializationPolicy
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.chapter.ChapterSanitizer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.asCoroutineDispatcher
import net.odorcave.kubinashi.gson.PageAdapter
import net.odorcave.kubinashi.model.Chapter
import org.slf4j.event.Level
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.server.applicationSetup
import java.io.Serializable
import java.util.concurrent.Executors

val LOG_TAG = "SOURCE_SERVICE"

@Immutable
data class SourceManga(val source: Long, val manga: SMangaImpl) : Serializable;

@Immutable
data class SourceChapter(val source: Long, val chapter: SChapterImpl) : Serializable;

@Immutable
data class SourcePage(val source: Long, val page: Page) : Serializable;

fun Application.routing() {
    val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    routing {
        // get("/search") {
        //     try {
        //         val query = call.request.queryParameters["q"]
        //         val language = call.request.queryParameters["l"] ?: "en"
        //         if (query == null) {
        //             call.respond<Map<Long, List<Manga>>>(mapOf())
        //             return@get
        //         }

        //         val sources = sourceManager.getCatalogueSources().filter { it.lang == language }

        //         val resultsMap = ConcurrentHashMap<Long, List<SManga>>()
        //         sources.map { source ->
        //             async {
        //                 try {
        //                     val page = withContext(coroutineDispatcher) {
        //                         source.getSearchManga(1, query, source.getFilterList())
        //                     }

        //                     val titles = page.mangas
        //                         .distinctBy { it.url }

        //                     resultsMap.set(source.id, titles)
        //                 } catch (e: Exception) {
        //                     Log.e(
        //                         LOG_TAG,
        //                         "Failed to query with error: ${e.message}\n${e.stackTraceToString()}",
        //                     )
        //                 }
        //             }
        //         }.awaitAll()

        //         call.respond(resultsMap.toMap())
        //     } catch (e: Exception) {
        //         Log.e(LOG_TAG, "Failed to query with error: ${e.message}\\n${e.stackTraceToString()} ")
        //     }
        // }

        get("/search/{sourceId}") {
            try {
                val query = call.request.queryParameters["q"]
                val language = call.request.queryParameters["l"] ?: "en"
                if (query == null) {
                    call.respond<Map<Long, List<Manga>>>(mapOf())
                    return@get
                }

                val sourceId = call.parameters["sourceId"]?.toLong()
                if (sourceId == null) {
                    call.response.status(HttpStatusCode.BadRequest)
                    return@get
                }

                val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
                if (source == null) {
                    log.warn("CatalogueSource not found for $sourceId")
                    call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                    return@get
                }

                val page = source.getSearchManga(1, query, source.getFilterList())
                val titles = page.mangas
                    .distinctBy { it.url }

                call.respond(titles)
            } catch (e: Exception) {
                // Log.e(LOG_TAG, "Failed to query with error: ${e.message}\\n${e.stackTraceToString()} ")
            }
        }

        get("/manga/details") {

            val mangaToFetch: SourceManga;
            try {
                mangaToFetch = call.receive<SourceManga>()
            } catch (e: ContentTransformationException) {
                // Log.i(LOG_TAG, "${e}");
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val sourceId = mangaToFetch.source

            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            val mangaDetails = source.getMangaDetails(mangaToFetch.manga)

            call.respond(mangaDetails)
        }

        get("/manga/chapters") {
            val mangaToFetch = call.receive<SourceManga>()
            val sourceId = mangaToFetch.source
            val manga = mangaToFetch.manga;

            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            val sourceChapters = source.getChapterList(manga).mapIndexed { i, sChapter ->
                Chapter.create()
                    .copyFromSChapter(sChapter)
                    .copy(name = with(ChapterSanitizer) {sChapter.name.sanitize(manga.title)})
                    .copy(source_order = i.toLong())
            }.map {
                var chapter = it
                if (source is HttpSource) {
                    val sChapter = chapter.toSChapter()
                    source.prepareNewChapter(sChapter, manga)
                    chapter = chapter.copyFromSChapter(sChapter)
                }

                chapter = chapter.copy(chapter_number = ChapterRecognition.parseChapterNumber(manga.title, chapter.name, chapter.chapter_number))

                return@map chapter
            }

            call.respond(sourceChapters)
        }

        get("/manga/chapter/pages") {
            val chapterToFetch = call.receive<SourceChapter>()
            val sourceId = chapterToFetch.source

            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            val pageList = source.getPageList(chapterToFetch.chapter)

            call.respond(pageList)
            return@get
        }

        get("/manga/chapter/page/image") {
            val page: SourcePage
            try {
                page = call.receive<SourcePage>()
            } catch (e: Exception) {
                // Log.e("LOG_TAG", "$e: ${e.stackTraceToString()}")
                throw e
            }
            val sourceId = page.source

            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            if (source is HttpSource) {
                val response = source.getImage(page.page)
                val bodyContentType = response.body.contentType()!!
                call.respondBytes(contentType = ContentType(bodyContentType.type, bodyContentType.subtype)) {
                    response.body.bytes()
                }
                return@get
            }
        }
    }
}


fun main() {
    applicationSetup()

    var server = embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            gson {
                // Needs to be parsed to string because JSON can't handle all Long values and truncates some
                // e.g: in memory 2499283573021220255 => 2499283573021220400 in JSON
                setLongSerializationPolicy(LongSerializationPolicy.STRING)

                registerTypeAdapter(Page::class.java, PageAdapter())
            }
        }
        install(CallLogging) {
            level = Level.INFO
        }

        routing()
    }

    server.start(wait = false)


}
