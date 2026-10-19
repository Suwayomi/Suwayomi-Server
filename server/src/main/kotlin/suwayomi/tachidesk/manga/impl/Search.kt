package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.javalin.http.BadRequestResponse
import io.javalin.json.JsonMapper
import io.javalin.json.fromJsonString
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.MangaList.processEntries
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrStub
import suwayomi.tachidesk.manga.model.dataclass.PagedMangaListDataClass
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

object Search {
    suspend fun sourceSearch(
        userId: Int,
        sourceId: Long,
        searchTerm: String,
        pageNum: Int,
    ): PagedMangaListDataClass {
        val source = getSourceOrStub(sourceId)
        val searchManga = source.getSearchManga(pageNum, searchTerm, getFilterListOf(userId, source))
        return searchManga.processEntries(sourceId)
    }

    suspend fun sourceFilter(
        sourceId: Long,
        pageNum: Int,
        filter: FilterData,
    ): PagedMangaListDataClass {
        val source = getSourceOrStub(sourceId)
        val filterList = if (filter.filter != null) buildFilterList(sourceId, filter.filter) else source.getFilterList()
        val searchManga = source.getSearchManga(pageNum, filter.searchTerm ?: "", filterList)
        return searchManga.processEntries(sourceId)
    }

    /**
     * Persisted filter state per `(userId, sourceId)`, stored as the list of applied
     * [FilterChange]s — never as a [FilterList]. `Filter` carries mutable `state` and
     * extensions implement the abstract subclasses with their own types, so a cached list
     * cannot be deep-copied: sharing one instance used to leak one user's filter changes
     * into every other user's search and let concurrent writers corrupt the list mid-read.
     * Each request re-applies the stored changes onto a fresh `source.getFilterList()`,
     * giving every caller an isolated instance.
     */
    private val filterChangesCache = ConcurrentHashMap<Pair<Int, Long>, List<FilterChange>>()

    private fun getFilterListOf(
        userId: Int,
        source: Source,
        reset: Boolean = false,
    ): FilterList {
        if (reset) {
            filterChangesCache.remove(userId to source.id)
        }
        val filterList = source.getFilterList()
        filterChangesCache[userId to source.id]
            ?.takeIf { it.isNotEmpty() }
            // replay tolerates schema drift: an extension update may shrink or reorder
            // the filter list after changes were stored — stale entries are skipped
            // instead of breaking every search for that user (same as before: the old
            // shared cache also ignored out-of-range positions silently)
            ?.let { updateFilterList(filterList, it, lenient = true) }
        return filterList
    }

    suspend fun getFilterList(
        userId: Int,
        sourceId: Long,
        reset: Boolean,
    ): List<FilterObject> {
        val source = getSourceOrStub(sourceId)

        return getFilterListOf(userId, source, reset).list.map {
            FilterObject(
                when (it) {
                    is Filter.Header -> "Header"
                    is Filter.Separator -> "Separator"
                    is Filter.Select<*> -> "Select"
                    is Filter.Text -> "Text"
                    is Filter.CheckBox -> "CheckBox"
                    is Filter.TriState -> "TriState"
                    is Filter.Group<*> -> "Group"
                    is Filter.Sort -> "Sort"
                    else -> throw RuntimeException("sealed class Cannot have more Subtypes!")
                },
                when (it) {
                    is Filter.Group<*> -> {
                        SerializableGroup(
                            it.name,
                            it.state.map { item ->
                                when (item) {
                                    is Filter.CheckBox -> FilterObject("CheckBox", item)
                                    is Filter.TriState -> FilterObject("TriState", item)
                                    is Filter.Text -> FilterObject("Text", item)
                                    is Filter.Select<*> -> FilterObject("Select", item)
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

    private fun Filter.Select<*>.getValuesType(): String = values::class.java.componentType!!.simpleName

    class SerializableGroup(
        name: String,
        state: List<FilterObject>,
    ) : Filter<List<FilterObject>>(name, state)

    data class FilterObject(
        val type: String,
        val filter: Filter<*>,
    )

    suspend fun setFilter(
        userId: Int,
        sourceId: Long,
        changes: List<FilterChange>,
    ) {
        val source = getSourceOrStub(sourceId)
        val key = userId to source.id
        // the whole read-merge-write runs inside `compute`: a plain `cache[key] =`
        // is a lost-update race — two concurrent setFilter calls would each persist
        // only their own snapshot and silently drop the other's changes
        filterChangesCache.compute(key) { _, stored ->
            // auto-recover the stored baseline first: an extension update may shrink or
            // reorder the filter list, and a stored change that no longer applies would
            // otherwise make strict validation fail on every future write — a permanent
            // 400 until a manual reset. Dropping it matches the lenient replay the read
            // path already does.
            val filterList = source.getFilterList()
            val applicableChanges =
                stored.orEmpty().filter { change ->
                    try {
                        applyFilterChange(filterList, change)
                        true
                    } catch (e: BadRequestResponse) {
                        false
                    }
                }
            // validate the full pending patch set before persisting — a rejected change
            // must not corrupt the stored baseline (a `compute` block that throws
            // stores nothing)
            val pendingChanges = applicableChanges + changes
            updateFilterList(filterList, pendingChanges)
            deduplicateChanges(filterList, pendingChanges)
        }
    }

    /**
     * Collapse the patch set to what must be persisted: each change fully replaces its
     * filter's state, so per filter only the last change matters (also keeps the cache
     * bounded). [Filter.Group] is the exception — its `state` nests a [FilterChange]
     * targeting a single child, so several changes can share a group position while
     * toggling different children; deduplicating by position alone would silently drop
     * all but the last child change. The key is `(position, childPosition?)`: last
     * write wins per child, while changes for different children are all kept.
     */
    private fun deduplicateChanges(
        filterList: FilterList,
        changes: List<FilterChange>,
    ): List<FilterChange> =
        changes
            .groupBy { deduplicationKey(filterList, it) }
            .mapValues { (_, sameKeyChanges) -> sameKeyChanges.last() }
            .values
            .toList()

    private fun deduplicationKey(
        filterList: FilterList,
        change: FilterChange,
    ): Pair<Int, Int?> =
        change.position to
            (filterList.getOrNull(change.position) as? Filter.Group<*>)
                ?.let {
                    runCatching { jsonMapper.fromJsonString<FilterChange>(change.state) }
                        .getOrNull()
                        ?.position
                }

    private fun updateFilterList(
        filterList: FilterList,
        changes: List<FilterChange>,
        lenient: Boolean = false,
    ): FilterList {
        changes.forEach { change ->
            try {
                applyFilterChange(filterList, change)
            } catch (e: BadRequestResponse) {
                if (!lenient) throw e
            }
        }
        return filterList
    }

    private fun applyFilterChange(
        filterList: FilterList,
        change: FilterChange,
    ) {
        val filter =
            filterList.getOrNull(change.position)
                ?: throw BadRequestResponse(
                    "Invalid filter position ${change.position}: source provides ${filterList.size} filters",
                )
        when (filter) {
            is Filter.Header -> {
                // NOOP
            }

            is Filter.Separator -> {
                // NOOP
            }

            is Filter.Select<*> -> {
                val index =
                    change.state.toIntOrNull()
                        ?: throw BadRequestResponse("Invalid Select filter value '${change.state}'")
                if (index !in filter.values.indices) {
                    throw BadRequestResponse(
                        "Select filter index $index out of range: filter '${filter.name}' has ${filter.values.size} values",
                    )
                }
                filter.state = index
            }

            is Filter.Text -> {
                filter.state = change.state
            }

            is Filter.CheckBox -> {
                filter.state =
                    change.state.toBooleanStrictOrNull()
                        ?: throw BadRequestResponse("Invalid CheckBox filter value '${change.state}'")
            }

            is Filter.TriState -> {
                filter.state =
                    change.state.toIntOrNull()
                        ?: throw BadRequestResponse("Invalid TriState filter value '${change.state}'")
            }

            is Filter.Group<*> -> {
                val groupChange =
                    runCatching { jsonMapper.fromJsonString<FilterChange>(change.state) }
                        .getOrElse { throw BadRequestResponse("Invalid Group filter change '${change.state}'") }
                val groupFilter =
                    filter.state.getOrNull(groupChange.position)
                        ?: throw BadRequestResponse(
                            "Invalid group filter position ${groupChange.position}: " +
                                "group '${filter.name}' has ${filter.state.size} filters",
                        )

                when (groupFilter) {
                    is Filter.CheckBox -> {
                        groupFilter.state =
                            groupChange.state.toBooleanStrictOrNull()
                                ?: throw BadRequestResponse("Invalid CheckBox filter value '${groupChange.state}'")
                    }

                    is Filter.TriState -> {
                        groupFilter.state =
                            groupChange.state.toIntOrNull()
                                ?: throw BadRequestResponse("Invalid TriState filter value '${groupChange.state}'")
                    }

                    is Filter.Text -> {
                        groupFilter.state = groupChange.state
                    }

                    is Filter.Select<*> -> {
                        val index =
                            groupChange.state.toIntOrNull()
                                ?: throw BadRequestResponse("Invalid Select filter value '${groupChange.state}'")
                        if (index !in groupFilter.values.indices) {
                            throw BadRequestResponse(
                                "Select filter index $index out of range: " +
                                    "filter '${groupFilter.name}' has ${groupFilter.values.size} values",
                            )
                        }
                        groupFilter.state = index
                    }

                    else -> {
                        throw BadRequestResponse("Illegal Group item type!")
                    }
                }
            }

            is Filter.Sort -> {
                filter.state =
                    runCatching {
                        jsonMapper.fromJsonString<Filter.Sort.Selection>(change.state)
                    }.getOrElse { throw BadRequestResponse("Invalid Sort filter value '${change.state}'") }
            }

            else -> {
                throw BadRequestResponse("Unsupported filter type '${filter::class.simpleName}'")
            }
        }
    }

    suspend fun buildFilterList(
        sourceId: Long,
        changes: List<FilterChange>,
    ): FilterList {
        val source = getSourceOrStub(sourceId)
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

    /**
     * Global search across all sources is not implemented (the REST endpoint keeps
     * returning an empty result for API compatibility).
     */
    @Suppress("UNUSED_PARAMETER")
    fun sourceGlobalSearch(searchTerm: String) {
        // intentionally unimplemented — no upstream implementation exists to port
    }
}
