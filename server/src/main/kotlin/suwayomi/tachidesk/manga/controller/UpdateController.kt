package suwayomi.tachidesk.manga.controller

import io.javalin.http.Context
import io.javalin.http.HttpCode
import io.javalin.websocket.WsConfig
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.UpdaterSocket
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.*
import kotlin.collections.ArrayList

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

object UpdateController {
    private val logger = KotlinLogging.logger { }

    /** get recently updated manga chapters */
    fun recentChapters(ctx: Context) {
        ctx.future(
            future {
                Chapter.getRecentChapters()
            }
        )
    }

    fun categoryUpdate(ctx: Context) {
        val categoryId = Optional.ofNullable(ctx.formParam("category")).map { it.toIntOrNull() }.orElse(null)
        val categoriesForUpdate = ArrayList<CategoryDataClass>()
        if (categoryId == null) {
            logger.info { "Adding Library to Update Queue" }
            categoriesForUpdate.addAll(Category.getCategoryList())
        } else {
            val category = Category.getCategoryById(categoryId)
            if (category != null) {
                categoriesForUpdate.add(category)
            } else {
                logger.info { "No Category found" }
                ctx.status(HttpCode.BAD_REQUEST)
                return
            }
        }
        addCategoriesToUpdateQueue(categoriesForUpdate, true)
        ctx.status(HttpCode.OK)
    }

    private fun addCategoriesToUpdateQueue(categories: List<CategoryDataClass>, clear: Boolean = false) {
        val updater by DI.global.instance<IUpdater>()
        if (clear) {
            runBlocking { updater.reset() }
        }
        for (category in categories) {
            val mangas = CategoryManga.getCategoryMangaList(category.id)
            for (manga in mangas) {
                updater.addMangaToQueue(manga)
            }
        }
    }

    fun categoryUpdateWS(ws: WsConfig) {
        ws.onConnect { ctx ->
            UpdaterSocket.addClient(ctx)
        }
        ws.onMessage { ctx ->
            UpdaterSocket.handleRequest(ctx)
        }
        ws.onClose { ctx ->
            UpdaterSocket.removeClient(ctx)
        }
    }

    fun updateSummary(ctx: Context) {
        val updater by DI.global.instance<IUpdater>()
        ctx.json(updater.getStatus().value.toJson())
    }
}
