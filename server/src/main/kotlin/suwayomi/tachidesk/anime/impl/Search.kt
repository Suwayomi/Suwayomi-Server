package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import io.javalin.json.JsonMapper
import io.javalin.json.fromJsonString
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.anime.impl.AnimeList.processEntries
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.anime.model.dataclass.PagedAnimeListDataClass
import uy.kohesive.injekt.injectLazy

object Search {
    suspend fun sourceSearch(
        sourceId: Long,
        searchTerm: String,
        pageNum: Int,
    ): PagedAnimeListDataClass {
        val source = getCatalogueSourceOrStub(sourceId)
        val searchAnime = source.getSearchAnime(pageNum, searchTerm, getFilterListOf(source))
        return searchAnime.processEntries(sourceId)
    }

    suspend fun sourceFilter(
        sourceId: Long,
        pageNum: Int,
        filter: FilterData,
    ): PagedAnimeListDataClass {
        val source = getCatalogueSourceOrStub(sourceId)
        val filterList = if (filter.filter != null) buildFilterList(sourceId, filter.filter) else source.getFilterList()
        val searchAnime = source.getSearchAnime(pageNum, filter.searchTerm ?: "", filterList)
        return searchAnime.processEntries(sourceId)
    }

    private val filterListCache = mutableMapOf<Long, AnimeFilterList>()

    private fun getFilterListOf(
        source: AnimeCatalogueSource,
        reset: Boolean = false,
    ): AnimeFilterList {
        if (reset || !filterListCache.containsKey(source.id)) {
            filterListCache[source.id] = source.getFilterList()
        }
        return filterListCache[source.id]!!
    }

    fun getFilterList(
        sourceId: Long,
        reset: Boolean,
    ): List<FilterObject> {
        val source = getCatalogueSourceOrStub(sourceId)

        return getFilterListOf(source, reset).list.map {
            FilterObject(
                when (it) {
                    is AnimeFilter.Header -> "Header"
                    is AnimeFilter.Separator -> "Separator"
                    is AnimeFilter.Select<*> -> "Select"
                    is AnimeFilter.Text -> "Text"
                    is AnimeFilter.CheckBox -> "CheckBox"
                    is AnimeFilter.TriState -> "TriState"
                    is AnimeFilter.Group<*> -> "Group"
                    is AnimeFilter.Sort -> "Sort"
                    else -> throw RuntimeException("sealed class Cannot have more Subtypes!")
                },
                when (it) {
                    is AnimeFilter.Group<*> -> {
                        SerializableGroup(
                            it.name,
                            it.state.map { item ->
                                when (item) {
                                    is AnimeFilter.CheckBox -> FilterObject("CheckBox", item)
                                    is AnimeFilter.TriState -> FilterObject("TriState", item)
                                    is AnimeFilter.Text -> FilterObject("Text", item)
                                    is AnimeFilter.Select<*> -> FilterObject("Select", item)
                                    else -> throw RuntimeException("Illegal Group item type!")
                                }
                            },
                        )
                    }

                    else -> {
                        it
                    }
                },
            )
        }
    }

    class SerializableGroup(
        name: String,
        state: List<FilterObject>,
    ) : AnimeFilter<List<FilterObject>>(name, state)

    data class FilterObject(
        val type: String,
        val filter: AnimeFilter<*>,
    )

    fun setFilter(
        sourceId: Long,
        changes: List<FilterChange>,
    ) {
        val source = getCatalogueSourceOrStub(sourceId)
        val filterList = getFilterListOf(source, false)
        updateFilterList(filterList, changes)
    }

    private fun updateFilterList(
        filterList: AnimeFilterList,
        changes: List<FilterChange>,
    ): AnimeFilterList {
        changes.forEach { change ->
            when (val filter = filterList[change.position]) {
                is AnimeFilter.Header -> {
                    // NOOP
                }

                is AnimeFilter.Separator -> {
                    // NOOP
                }

                is AnimeFilter.Select<*> -> {
                    filter.state = change.state.toInt()
                }

                is AnimeFilter.Text -> {
                    filter.state = change.state
                }

                is AnimeFilter.CheckBox -> {
                    filter.state = change.state.toBooleanStrict()
                }

                is AnimeFilter.TriState -> {
                    filter.state = change.state.toInt()
                }

                is AnimeFilter.Group<*> -> {
                    val groupChange = jsonMapper.fromJsonString<FilterChange>(change.state)

                    when (val groupFilter = filter.state[groupChange.position]) {
                        is AnimeFilter.CheckBox -> groupFilter.state = groupChange.state.toBooleanStrict()
                        is AnimeFilter.TriState -> groupFilter.state = groupChange.state.toInt()
                        is AnimeFilter.Text -> groupFilter.state = groupChange.state
                        is AnimeFilter.Select<*> -> groupFilter.state = groupChange.state.toInt()
                    }
                }

                is AnimeFilter.Sort -> {
                    filter.state = jsonMapper.fromJsonString(change.state, AnimeFilter.Sort.Selection::class.java)
                }
            }
        }
        return filterList
    }

    fun buildFilterList(
        sourceId: Long,
        changes: List<FilterChange>,
    ): AnimeFilterList {
        val source = getCatalogueSourceOrStub(sourceId)
        val filterList = source.getFilterList()
        return updateFilterList(filterList, changes)
    }

    private val jsonMapper: JsonMapper by injectLazy()

    @Serializable
    data class FilterChange(
        val position: Int,
        val state: String,
    )

    @Serializable
    data class FilterData(
        val searchTerm: String?,
        val filter: List<FilterChange>?,
    )
}
