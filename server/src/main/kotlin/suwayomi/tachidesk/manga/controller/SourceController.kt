package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.MangaList
import suwayomi.tachidesk.manga.impl.Search
import suwayomi.tachidesk.manga.impl.Search.FilterChange
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.impl.Source.SourcePreferenceChange
import suwayomi.tachidesk.server.JavalinSetup.future

object SourceController {
    /** list of sources */
    fun list(ctx: Context) {
        ctx.json(Source.getSourceList())
    }

    /** fetch source with id `sourceId` */
    fun retrieve(ctx: Context) {
        val sourceId = ctx.pathParam("sourceId").toLong()
        ctx.json(Source.getSource(sourceId))
    }

    /** popular mangas from source with id `sourceId` */
    fun popular(ctx: Context) {
        val sourceId = ctx.pathParam("sourceId").toLong()
        val pageNum = ctx.pathParam("pageNum").toInt()
        ctx.future(
            future {
                MangaList.getMangaList(sourceId, pageNum, popular = true)
            }
        )
    }

    /** latest mangas from source with id `sourceId` */
    fun latest(ctx: Context) {
        val sourceId = ctx.pathParam("sourceId").toLong()
        val pageNum = ctx.pathParam("pageNum").toInt()
        ctx.future(
            future {
                MangaList.getMangaList(sourceId, pageNum, popular = false)
            }
        )
    }

    /** fetch preferences of source with id `sourceId` */
    fun getPreferences(ctx: Context) {
        val sourceId = ctx.pathParam("sourceId").toLong()
        ctx.json(Source.getSourcePreferences(sourceId))
    }

    /** set one preference of source with id `sourceId` */
    fun setPreference(ctx: Context) {
        val sourceId = ctx.pathParam("sourceId").toLong()
        val preferenceChange = ctx.bodyAsClass(SourcePreferenceChange::class.java)
        ctx.json(Source.setSourcePreference(sourceId, preferenceChange))
    }

    /** fetch filters of source with id `sourceId` */
    fun getFilters(ctx: Context) {
        val sourceId = ctx.pathParam("sourceId").toLong()
        val reset = ctx.queryParam("reset")?.toBoolean() ?: false
        ctx.json(Search.getFilterList(sourceId, reset))
    }

    private val json by DI.global.instance<Json>()

    /** change filters of source with id `sourceId` */
    fun setFilters(ctx: Context) {
        val sourceId = ctx.pathParam("sourceId").toLong()
        val filterChange = try {
            json.decodeFromString<List<FilterChange>>(ctx.body())
        } catch (e: Exception) {
            listOf(json.decodeFromString<FilterChange>(ctx.body()))
        }

        ctx.json(Search.setFilter(sourceId, filterChange))
    }

    /** single source search */
    fun searchSingle(ctx: Context) {
        val sourceId = ctx.pathParam("sourceId").toLong()
        val searchTerm = ctx.queryParam("searchTerm") ?: ""
        val pageNum = ctx.queryParam("pageNum")?.toInt() ?: 1
        ctx.future(future { Search.sourceSearch(sourceId, searchTerm, pageNum) })
    }

    /** all source search */
    fun searchAll(ctx: Context) { // TODO
        val searchTerm = ctx.pathParam("searchTerm")
        ctx.json(Search.sourceGlobalSearch(searchTerm))
    }
}
