package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga

object CategoryController {
    /** category list */
    fun categoryList(ctx: Context) {
        ctx.json(Category.getCategoryList())
    }

    /** category create */
    fun categoryCreate(ctx: Context) {
        val name = ctx.formParam("name")!!
        Category.createCategory(name)
        ctx.status(200)
    }

    /** category modification */
    fun categoryModify(ctx: Context) {
        val categoryId = ctx.pathParam("categoryId").toInt()
        val name = ctx.formParam("name")
        val isDefault = ctx.formParam("default")?.toBoolean()
        Category.updateCategory(categoryId, name, isDefault)
        ctx.status(200)
    }

    /** category delete */
    fun categoryDelete(ctx: Context) {
        val categoryId = ctx.pathParam("categoryId").toInt()
        Category.removeCategory(categoryId)
        ctx.status(200)
    }

    /** returns the manga list associated with a category */
    fun categoryMangas(ctx: Context) {
        val categoryId = ctx.pathParam("categoryId").toInt()
        ctx.json(CategoryManga.getCategoryMangaList(categoryId))
    }

    /** category re-ordering */
    fun categoryReorder(ctx: Context) {
        val from = ctx.formParam("from")!!.toInt()
        val to = ctx.formParam("to")!!.toInt()
        Category.reorderCategory(from, to)
        ctx.status(200)
    }
}
