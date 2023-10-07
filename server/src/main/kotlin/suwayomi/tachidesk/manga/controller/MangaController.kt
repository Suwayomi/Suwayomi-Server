package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpCode
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.Library
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.Page
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReadyByIndex
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.formParam
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation
import kotlin.time.Duration.Companion.days

object MangaController {
    private val json by DI.global.instance<Json>()

    val retrieve =
        handler(
            pathParam<Int>("mangaId"),
            queryParam("onlineFetch", false),
            documentWith = {
                withOperation {
                    summary("Get manga info")
                    description("Get a manga from the database using a specific id.")
                }
            },
            behaviorOf = { ctx, mangaId, onlineFetch ->
                ctx.future(
                    future {
                        Manga.getManga(mangaId, onlineFetch)
                    },
                )
            },
            withResults = {
                json<MangaDataClass>(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** get manga info with all data filled in */
    val retrieveFull =
        handler(
            pathParam<Int>("mangaId"),
            queryParam("onlineFetch", false),
            documentWith = {
                withOperation {
                    summary("Get manga info with all data filled in")
                    description("Get a manga from the database using a specific id.")
                }
            },
            behaviorOf = { ctx, mangaId, onlineFetch ->
                ctx.future(
                    future {
                        Manga.getMangaFull(mangaId, onlineFetch)
                    },
                )
            },
            withResults = {
                json<MangaDataClass>(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** manga thumbnail */
    val thumbnail =
        handler(
            pathParam<Int>("mangaId"),
            documentWith = {
                withOperation {
                    summary("Get a manga thumbnail")
                    description("Get a manga thumbnail from the source or the cache.")
                }
            },
            behaviorOf = { ctx, mangaId ->
                ctx.future(
                    future { Manga.getMangaThumbnail(mangaId) }
                        .thenApply {
                            ctx.header("content-type", it.second)
                            val httpCacheSeconds = 1.days.inWholeSeconds
                            ctx.header("cache-control", "max-age=$httpCacheSeconds")
                            it.first
                        },
                )
            },
            withResults = {
                image(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** adds the manga to library */
    val addToLibrary =
        handler(
            pathParam<Int>("mangaId"),
            documentWith = {
                withOperation {
                    summary("Add manga to library")
                    description("Use a manga id to add the manga to your library.\nWill do nothing if manga is already in your library.")
                }
            },
            behaviorOf = { ctx, mangaId ->
                ctx.future(
                    future { Library.addMangaToLibrary(mangaId) },
                )
            },
            withResults = {
                httpCode(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** removes the manga from the library */
    val removeFromLibrary =
        handler(
            pathParam<Int>("mangaId"),
            documentWith = {
                withOperation {
                    summary("Remove manga to library")
                    description("Use a manga id to remove the manga to your library.\nWill do nothing if manga not in your library.")
                }
            },
            behaviorOf = { ctx, mangaId ->
                ctx.future(
                    future { Library.removeMangaFromLibrary(mangaId) },
                )
            },
            withResults = {
                httpCode(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** list manga's categories */
    val categoryList =
        handler(
            pathParam<Int>("mangaId"),
            documentWith = {
                withOperation {
                    summary("Get a manga's categories")
                    description("Get the list of categories for this manga")
                }
            },
            behaviorOf = { ctx, mangaId ->
                ctx.json(CategoryManga.getMangaCategories(mangaId))
            },
            withResults = {
                json<Array<CategoryDataClass>>(HttpCode.OK)
            },
        )

    /** adds the manga to category */
    val addToCategory =
        handler(
            pathParam<Int>("mangaId"),
            pathParam<Int>("categoryId"),
            documentWith = {
                withOperation {
                    summary("Add manga to category")
                    description("Add a manga to a category using their ids.")
                }
            },
            behaviorOf = { ctx, mangaId, categoryId ->
                CategoryManga.addMangaToCategory(mangaId, categoryId)
                ctx.status(200)
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    /** removes the manga from the category */
    val removeFromCategory =
        handler(
            pathParam<Int>("mangaId"),
            pathParam<Int>("categoryId"),
            documentWith = {
                withOperation {
                    summary("Remove manga from category")
                    description("Remove a manga from a category using their ids.")
                }
            },
            behaviorOf = { ctx, mangaId, categoryId ->
                CategoryManga.removeMangaFromCategory(mangaId, categoryId)
                ctx.status(200)
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    /** used to modify a manga's meta parameters */
    val meta =
        handler(
            pathParam<Int>("mangaId"),
            formParam<String>("key"),
            formParam<String>("value"),
            documentWith = {
                withOperation {
                    summary("Add data to manga")
                    description("A simple Key-Value storage in the manga object, you can set values for whatever you want inside it.")
                }
            },
            behaviorOf = { ctx, mangaId, key, value ->
                Manga.modifyMangaMeta(mangaId, key, value)
                ctx.status(200)
            },
            withResults = {
                httpCode(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** get chapter list when showing a manga */
    val chapterList =
        handler(
            pathParam<Int>("mangaId"),
            queryParam("onlineFetch", false),
            documentWith = {
                withOperation {
                    summary("Get manga chapter list")
                    description(
                        "Get the manga chapter list from the database or online. " +
                            "If there is no chapters in the database it fetches the chapters online. " +
                            "Use onlineFetch to update chapter list.",
                    )
                }
            },
            behaviorOf = { ctx, mangaId, onlineFetch ->
                ctx.future(future { Chapter.getChapterList(mangaId, onlineFetch) })
            },
            withResults = {
                json<Array<ChapterDataClass>>(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** batch edit chapters of single manga */
    val chapterBatch =
        handler(
            pathParam<Int>("mangaId"),
            documentWith = {
                withOperation {
                    summary("Chapters update multiple")
                    description("Update multiple chapters of single manga. For batch marking as read, or bookmarking")
                }
                body<Chapter.MangaChapterBatchEditInput>()
            },
            behaviorOf = { ctx, mangaId ->
                val input = json.decodeFromString<Chapter.MangaChapterBatchEditInput>(ctx.body())
                Chapter.modifyChapters(input, mangaId)
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    /** batch edit chapters from multiple manga */
    val anyChapterBatch =
        handler(
            documentWith = {
                withOperation {
                    summary("Chapters update multiple")
                    description("Update multiple chapters on any manga. For batch marking as read, or bookmarking")
                }
                body<Chapter.ChapterBatchEditInput>()
            },
            behaviorOf = { ctx ->
                val input = json.decodeFromString<Chapter.ChapterBatchEditInput>(ctx.body())
                Chapter.modifyChapters(
                    Chapter.MangaChapterBatchEditInput(
                        input.chapterIds,
                        null,
                        input.change,
                    ),
                )
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    /** used to display a chapter, get a chapter in order to show its pages */
    val chapterRetrieve =
        handler(
            pathParam<Int>("mangaId"),
            pathParam<Int>("chapterIndex"),
            documentWith = {
                withOperation {
                    summary("Get a chapter")
                    description("Get the chapter from the manga id and chapter index. It will also retrieve the pages for this chapter.")
                }
            },
            behaviorOf = { ctx, mangaId, chapterIndex ->
                ctx.future(future { getChapterDownloadReadyByIndex(chapterIndex, mangaId) })
            },
            withResults = {
                json<ChapterDataClass>(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** used to modify a chapter's parameters */
    val chapterModify =
        handler(
            pathParam<Int>("mangaId"),
            pathParam<Int>("chapterIndex"),
            formParam<Boolean?>("read"),
            formParam<Boolean?>("bookmarked"),
            formParam<Boolean?>("markPrevRead"),
            formParam<Int?>("lastPageRead"),
            documentWith = {
                withOperation {
                    summary("Modify a chapter")
                    description("Update user info for a given chapter, such as read status, bookmarked, and more.")
                }
            },
            behaviorOf = { ctx, mangaId, chapterIndex, read, bookmarked, markPrevRead, lastPageRead ->
                Chapter.modifyChapter(mangaId, chapterIndex, read, bookmarked, markPrevRead, lastPageRead)

                ctx.status(200)
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    /** delete a downloaded chapter */
    val chapterDelete =
        handler(
            pathParam<Int>("mangaId"),
            pathParam<Int>("chapterIndex"),
            documentWith = {
                withOperation {
                    summary("Delete a chapter download")
                    description("Delete the downloaded chapter and its files.")
                }
            },
            behaviorOf = { ctx, mangaId, chapterIndex ->
                Chapter.deleteChapter(mangaId, chapterIndex)

                ctx.status(200)
            },
            withResults = {
                httpCode(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** used to modify a chapter's meta parameters */
    val chapterMeta =
        handler(
            pathParam<Int>("mangaId"),
            pathParam<Int>("chapterIndex"),
            formParam<String>("key"),
            formParam<String>("value"),
            documentWith = {
                withOperation {
                    summary("Add data to chapter")
                    description("A simple Key-Value storage in the chapter object, you can set values for whatever you want inside it.")
                }
            },
            behaviorOf = { ctx, mangaId, chapterIndex, key, value ->
                Chapter.modifyChapterMeta(mangaId, chapterIndex, key, value)

                ctx.status(200)
            },
            withResults = {
                httpCode(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** get page at index "index" */
    val pageRetrieve =
        handler(
            pathParam<Int>("mangaId"),
            pathParam<Int>("chapterIndex"),
            pathParam<Int>("index"),
            documentWith = {
                withOperation {
                    summary("Get a chapter page")
                    description(
                        "Get a chapter page for a given index. Cache use can be disabled so it only retrieves it directly from the source.",
                    )
                }
            },
            behaviorOf = { ctx, mangaId, chapterIndex, index ->
                ctx.future(
                    future { Page.getPageImage(mangaId, chapterIndex, index) }
                        .thenApply {
                            ctx.header("content-type", it.second)
                            val httpCacheSeconds = 1.days.inWholeSeconds
                            ctx.header("cache-control", "max-age=$httpCacheSeconds")
                            it.first
                        },
                )
            },
            withResults = {
                image(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )
}
