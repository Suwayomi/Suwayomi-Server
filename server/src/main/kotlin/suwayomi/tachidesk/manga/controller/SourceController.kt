package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpCode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.MangaList
import suwayomi.tachidesk.manga.impl.Search
import suwayomi.tachidesk.manga.impl.Search.FilterChange
import suwayomi.tachidesk.manga.impl.Search.FilterData
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.impl.Source.SourcePreferenceChange
import suwayomi.tachidesk.manga.model.dataclass.PagedMangaListDataClass
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation

object SourceController {
    /** list of sources */
    val list =
        handler(
            documentWith = {
                withOperation {
                    summary("Sources list")
                    description("List of sources")
                }
            },
            behaviorOf = { ctx ->
                ctx.json(Source.getSourceList())
            },
            withResults = {
                json<Array<SourceDataClass>>(HttpCode.OK)
            },
        )

    /** fetch source with id `sourceId` */
    val retrieve =
        handler(
            pathParam<Long>("sourceId"),
            documentWith = {
                withOperation {
                    summary("Source fetch")
                    description("Fetch source with id `sourceId`")
                }
            },
            behaviorOf = { ctx, sourceId ->
                ctx.json(Source.getSource(sourceId)!!)
            },
            withResults = {
                json<SourceDataClass>(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    /** popular mangas from source with id `sourceId` */
    val popular =
        handler(
            pathParam<Long>("sourceId"),
            pathParam<Int>("pageNum"),
            documentWith = {
                withOperation {
                    summary("Source popular manga")
                    description("Popular mangas from source with id `sourceId`")
                }
            },
            behaviorOf = { ctx, sourceId, pageNum ->
                ctx.future(
                    future {
                        MangaList.getMangaList(sourceId, pageNum, popular = true)
                    },
                )
            },
            withResults = {
                json<PagedMangaListDataClass>(HttpCode.OK)
            },
        )

    /** latest mangas from source with id `sourceId` */
    val latest =
        handler(
            pathParam<Long>("sourceId"),
            pathParam<Int>("pageNum"),
            documentWith = {
                withOperation {
                    summary("Source latest manga")
                    description("Latest mangas from source with id `sourceId`")
                }
            },
            behaviorOf = { ctx, sourceId, pageNum ->
                ctx.future(
                    future {
                        MangaList.getMangaList(sourceId, pageNum, popular = false)
                    },
                )
            },
            withResults = {
                json<PagedMangaListDataClass>(HttpCode.OK)
            },
        )

    /** fetch preferences of source with id `sourceId` */
    val getPreferences =
        handler(
            pathParam<Long>("sourceId"),
            documentWith = {
                withOperation {
                    summary("Source preferences")
                    description("Fetch preferences of source with id `sourceId`")
                }
            },
            behaviorOf = { ctx, sourceId ->
                ctx.json(Source.getSourcePreferences(sourceId))
            },
            withResults = {
                json<Array<Source.PreferenceObject>>(HttpCode.OK)
            },
        )

    /** set one preference of source with id `sourceId` */
    val setPreference =
        handler(
            pathParam<Long>("sourceId"),
            documentWith = {
                withOperation {
                    summary("Source preference set")
                    description("Set one preference of source with id `sourceId`")
                }
                body<SourcePreferenceChange>()
            },
            behaviorOf = { ctx, sourceId ->
                val preferenceChange = ctx.bodyAsClass(SourcePreferenceChange::class.java)
                ctx.json(Source.setSourcePreference(sourceId, preferenceChange.position, preferenceChange.value))
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    /** fetch filters of source with id `sourceId` */
    val getFilters =
        handler(
            pathParam<Long>("sourceId"),
            queryParam("reset", false),
            documentWith = {
                withOperation {
                    summary("Source filters")
                    description("Fetch filters of source with id `sourceId`")
                }
            },
            behaviorOf = { ctx, sourceId, reset ->
                ctx.json(Search.getFilterList(sourceId, reset))
            },
            withResults = {
                json<Array<Search.FilterObject>>(HttpCode.OK)
            },
        )

    private val json by DI.global.instance<Json>()

    /** change filters of source with id `sourceId` */
    val setFilters =
        handler(
            pathParam<Long>("sourceId"),
            documentWith = {
                withOperation {
                    summary("Source filters set")
                    description("Change filters of source with id `sourceId`")
                }
                body<FilterChange>()
                body<Array<FilterChange>>()
            },
            behaviorOf = { ctx, sourceId ->
                val filterChange =
                    try {
                        json.decodeFromString<List<FilterChange>>(ctx.body())
                    } catch (e: Exception) {
                        listOf(json.decodeFromString<FilterChange>(ctx.body()))
                    }

                ctx.json(Search.setFilter(sourceId, filterChange))
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    /** single source search */
    val searchSingle =
        handler(
            pathParam<Long>("sourceId"),
            queryParam("searchTerm", ""),
            queryParam("pageNum", 1),
            documentWith = {
                withOperation {
                    summary("Source search")
                    description("Single source search")
                }
            },
            behaviorOf = { ctx, sourceId, searchTerm, pageNum ->
                ctx.future(future { Search.sourceSearch(sourceId, searchTerm, pageNum) })
            },
            withResults = {
                json<PagedMangaListDataClass>(HttpCode.OK)
            },
        )

    /** quick search single source filter */
    val quickSearchSingle =
        handler(
            pathParam<Long>("sourceId"),
            queryParam("pageNum", 1),
            documentWith = {
                withOperation {
                    summary("Source manga quick search")
                    description("Returns list of manga from source matching posted searchTerm and filter")
                }
                body<FilterData>()
            },
            behaviorOf = { ctx, sourceId, pageNum ->
                val filter = json.decodeFromString<FilterData>(ctx.body())
                ctx.future(future { Search.sourceFilter(sourceId, pageNum, filter) })
            },
            withResults = {
                json<PagedMangaListDataClass>(HttpCode.OK)
            },
        )

    /** all source search */
    val searchAll =
        handler(
            pathParam<String>("searchTerm"),
            documentWith = {
                withOperation {
                    summary("Source global search")
                    description("All source search")
                }
            },
            behaviorOf = { ctx, searchTerm -> // TODO
                ctx.json(Search.sourceGlobalSearch(searchTerm))
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )
}
