package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.Library
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.Page
import suwayomi.tachidesk.server.JavalinSetup.future

object MangaController {
    /** get manga info */
    fun retrieve(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()
        val onlineFetch = ctx.queryParam("onlineFetch")?.toBoolean() ?: false

        ctx.future(
            future {
                Manga.getManga(mangaId, onlineFetch)
            }
        )
    }

    /** manga thumbnail */
    fun thumbnail(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()

        ctx.future(
            future { Manga.getMangaThumbnail(mangaId) }
                .thenApply {
                    ctx.header("content-type", it.second)
                    it.first
                }
        )
    }

    /** adds the manga to library */
    fun addToLibrary(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()

        ctx.future(
            future { Library.addMangaToLibrary(mangaId) }
        )
    }

    /** removes the manga from the library */
    fun removeFromLibrary(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()

        ctx.future(
            future { Library.removeMangaFromLibrary(mangaId) }
        )
    }

    /** list manga's categories */
    fun categoryList(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()
        ctx.json(CategoryManga.getMangaCategories(mangaId))
    }

    /** adds the manga to category */
    fun addToCategory(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()
        val categoryId = ctx.pathParam("categoryId").toInt()
        CategoryManga.addMangaToCategory(mangaId, categoryId)
        ctx.status(200)
    }

    /** removes the manga from the category */
    fun removeFromCategory(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()
        val categoryId = ctx.pathParam("categoryId").toInt()
        CategoryManga.removeMangaFromCategory(mangaId, categoryId)
        ctx.status(200)
    }

    /** used to modify a manga's meta parameters */
    fun meta(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()

        val key = ctx.formParam("key")!!
        val value = ctx.formParam("value")!!

        Manga.modifyMangaMeta(mangaId, key, value)

        ctx.status(200)
    }

    /** get chapter list when showing a manga */
    fun chapterList(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()

        val onlineFetch = ctx.queryParam("onlineFetch")?.toBoolean() ?: false

        ctx.future(future { Chapter.getChapterList(mangaId, onlineFetch) })
    }

    /** used to display a chapter, get a chapter in order to show its pages */
    fun chapterRetrieve(ctx: Context) {
        val chapterIndex = ctx.pathParam("chapterIndex").toInt()
        val mangaId = ctx.pathParam("mangaId").toInt()
        ctx.future(future { Chapter.getChapter(chapterIndex, mangaId) })
    }

    /** used to modify a chapter's parameters */
    fun chapterModify(ctx: Context) {
        val chapterIndex = ctx.pathParam("chapterIndex").toInt()
        val mangaId = ctx.pathParam("mangaId").toInt()

        val read = ctx.formParam("read")?.toBoolean()
        val bookmarked = ctx.formParam("bookmarked")?.toBoolean()
        val markPrevRead = ctx.formParam("markPrevRead")?.toBoolean()
        val lastPageRead = ctx.formParam("lastPageRead")?.toInt()

        Chapter.modifyChapter(mangaId, chapterIndex, read, bookmarked, markPrevRead, lastPageRead)

        ctx.status(200)
    }

    /** used to modify a chapter's meta parameters */
    fun chapterMeta(ctx: Context) {
        val chapterIndex = ctx.pathParam("chapterIndex").toInt()
        val mangaId = ctx.pathParam("mangaId").toInt()

        val key = ctx.formParam("key")!!
        val value = ctx.formParam("value")!!

        Chapter.modifyChapterMeta(mangaId, chapterIndex, key, value)

        ctx.status(200)
    }

    /** get page at index "index" */
    fun pageRetrieve(ctx: Context) {
        val mangaId = ctx.pathParam("mangaId").toInt()
        val chapterIndex = ctx.pathParam("chapterIndex").toInt()
        val index = ctx.pathParam("index").toInt()

        ctx.future(
            future { Page.getPageImage(mangaId, chapterIndex, index) }
                .thenApply {
                    ctx.header("content-type", it.second)
                    it.first
                }
        )
    }
}
