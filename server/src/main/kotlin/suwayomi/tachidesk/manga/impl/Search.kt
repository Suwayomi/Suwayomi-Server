package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import suwayomi.tachidesk.manga.impl.MangaList.processEntries
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.model.dataclass.PagedMangaListDataClass

object Search {
    suspend fun sourceSearch(sourceId: Long, searchTerm: String, pageNum: Int): PagedMangaListDataClass {
        val source = getCatalogueSourceOrStub(sourceId)
        val searchManga = source.fetchSearchManga(pageNum, searchTerm, getFilterListOf(sourceId)).awaitSingle()
        return searchManga.processEntries(sourceId)
    }

    private val filterListCache = mutableMapOf<Long, FilterList>()

    private fun getFilterListOf(sourceId: Long, reset: Boolean = false): FilterList {
        if (reset || !filterListCache.containsKey(sourceId)) {
            filterListCache[sourceId] = getCatalogueSourceOrStub(sourceId).getFilterList()
        }
        return filterListCache[sourceId]!!
    }

    fun getInitialFilterList(sourceId: Long, reset: Boolean): List<FilterObject> {
        return getFilterListOf(sourceId, reset).list.map {
            FilterObject(
                when (it) {
                    is Filter.Header -> "Header"
                    is Filter.Separator -> "Separator"
                    is Filter.CheckBox -> "CheckBox"
                    is Filter.TriState -> "TriState"
                    is Filter.Text -> "Text"
                    is Filter.Select<*> -> "Select"
                    is Filter.Group<*> -> "Group"
                    is Filter.Sort -> "Sort"
                },
//                when (it) {
//                    is Filter.Select<*> -> it.getValuesType()
//                    else -> null
//                },
                it
            )
        }
    }

//    private fun Filter.Select<*>.getValuesType(): String = values::class.java.componentType!!.simpleName

    data class FilterObject(
        val type: String,
        val filter: Filter<*>
    )

    @Suppress("UNUSED_PARAMETER")
    fun sourceGlobalSearch(searchTerm: String) {
        // TODO
    }

    /**
     * Note: Exhentai had a filter serializer (now in SY) that we might be able to steal
     */
// private fun FilterList.toFilterWrapper(): List<FilterWrapper> {
//    return mapNotNull { filter ->
//        when (filter) {
//            is Filter.Header -> FilterWrapper("Header",filter)
//            is Filter.Separator -> FilterWrapper("Separator",filter)
//            is Filter.CheckBox -> FilterWrapper("CheckBox",filter)
//            is Filter.TriState -> FilterWrapper("TriState",filter)
//            is Filter.Text -> FilterWrapper("Text",filter)
//            is Filter.Select<*> -> FilterWrapper("Select",filter)
//            is Filter.Group<*> -> {
//                val group = GroupItem(filter)
//                val subItems = filter.state.mapNotNull {
//                    when (it) {
//                        is Filter.CheckBox -> FilterWrapper("CheckBox",filter)
//                        is Filter.TriState -> FilterWrapper("TriState",filter)
//                        is Filter.Text -> FilterWrapper("Text",filter)
//                        is Filter.Select<*> -> FilterWrapper("Select",filter)
//                        else -> null
//                    } as? ISectionable<*, *>
//                }
//                subItems.forEach { it.header = group }
//                group.subItems = subItems
//                group
//            }
//            is Filter.Sort -> {
//                val group = SortGroup(filter)
//                val subItems = filter.values.map {
//                    SortItem(it, group)
//                }
//                group.subItems = subItems
//                group
//            }
//        }
//    }
// }
}
