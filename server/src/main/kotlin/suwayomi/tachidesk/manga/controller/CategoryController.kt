package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpCode
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.server.util.formParam
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.withOperation

object CategoryController {
    /** category list */
    val categoryList = handler(
        documentWith = {
            withOperation {
                summary("Category list")
                description("get a list of categories")
            }
        },
        behaviorOf = { ctx ->
            ctx.json(Category.getCategoryList())
        },
        withResults = {
            json<List<CategoryDataClass>>(HttpCode.OK)
        }
    )

    /** category create */
    val categoryCreate = handler(
        formParam<String>("name"),
        documentWith = {
            withOperation {
                summary("Category create")
                description("Create a category")
            }
        },
        behaviorOf = { ctx, name ->
            if (Category.createCategory(name) != -1) {
                ctx.status(200)
            } else {
                ctx.status(HttpCode.BAD_REQUEST)
            }
        },
        withResults = {
            httpCode(HttpCode.OK)
            httpCode(HttpCode.BAD_REQUEST)
        }
    )

    /** category modification */
    val categoryModify = handler(
        pathParam<Int>("categoryId"),
        formParam<String?>("name"),
        formParam<Boolean?>("default"),
        documentWith = {
            withOperation {
                summary("Category modify")
                description("Modify a category")
            }
        },
        behaviorOf = { ctx, categoryId, name, isDefault ->
            Category.updateCategory(categoryId, name, isDefault)
            ctx.status(200)
        },
        withResults = {
            httpCode(HttpCode.OK)
        }
    )

    /** category delete */
    val categoryDelete = handler(
        pathParam<Int>("categoryId"),
        documentWith = {
            withOperation {
                summary("Category delete")
                description("Delete a category")
            }
        },
        behaviorOf = { ctx, categoryId ->
            Category.removeCategory(categoryId)
            ctx.status(200)
        },
        withResults = {
            httpCode(HttpCode.OK)
        }
    )

    /** returns the manga list associated with a category */
    val categoryMangas = handler(
        pathParam<Int>("categoryId"),
        documentWith = {
            withOperation {
                summary("Category manga")
                description("Returns the manga list associated with a category")
            }
        },
        behaviorOf = { ctx, categoryId ->
            ctx.json(CategoryManga.getCategoryMangaList(categoryId))
        },
        withResults = {
            json<List<MangaDataClass>>(HttpCode.OK)
        }
    )

    /** category re-ordering */
    val categoryReorder = handler(
        formParam<Int>("from"),
        formParam<Int>("to"),
        documentWith = {
            withOperation {
                summary("Category re-ordering")
                description("Re-order a category")
            }
        },
        behaviorOf = { ctx, from, to ->
            Category.reorderCategory(from, to)
            ctx.status(200)
        },
        withResults = {
            httpCode(HttpCode.OK)
        }
    )
}
