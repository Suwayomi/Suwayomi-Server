/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.impl.Source.getSourcePreferencesRaw
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import java.util.concurrent.CompletableFuture
import androidx.preference.CheckBoxPreference as SourceCheckBoxPreference
import androidx.preference.EditTextPreference as SourceEditTextPreference
import androidx.preference.ListPreference as SourceListPreference
import androidx.preference.MultiSelectListPreference as SourceMultiSelectListPreference
import androidx.preference.Preference as SourcePreference
import androidx.preference.SwitchPreferenceCompat as SourceSwitchPreference
import eu.kanade.tachiyomi.source.model.Filter as SourceFilter

class SourceType(
    val id: Long,
    val name: String,
    val lang: String,
    val iconUrl: String,
    val supportsLatest: Boolean,
    val isConfigurable: Boolean,
    val isNsfw: Boolean,
    val displayName: String,
) : Node {
    constructor(source: SourceDataClass) : this(
        id = source.id.toLong(),
        name = source.name,
        lang = source.lang,
        iconUrl = source.iconUrl,
        supportsLatest = source.supportsLatest,
        isConfigurable = source.isConfigurable,
        isNsfw = source.isNsfw,
        displayName = source.displayName,
    )

    constructor(row: ResultRow, sourceExtension: ResultRow, catalogueSource: CatalogueSource) : this(
        id = row[SourceTable.id].value,
        name = row[SourceTable.name],
        lang = row[SourceTable.lang],
        iconUrl = Extension.getExtensionIconUrl(sourceExtension[ExtensionTable.apkName]),
        supportsLatest = catalogueSource.supportsLatest,
        isConfigurable = catalogueSource is ConfigurableSource,
        isNsfw = row[SourceTable.isNsfw],
        displayName = catalogueSource.toString(),
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaNodeList> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, MangaNodeList>("MangaForSourceDataLoader", id)
    }

    fun extension(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ExtensionType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, ExtensionType>("ExtensionForSourceDataLoader", id)
    }

    fun preferences(): List<Preference> {
        return getSourcePreferencesRaw(id).map { preferenceOf(it) }
    }

    fun filters(): List<Filter> {
        return getCatalogueSourceOrStub(id).getFilterList().map { filterOf(it) }
    }

    fun meta(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<SourceMetaType>> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, List<SourceMetaType>>("SourceMetaDataLoader", id)
    }
}

@Suppress("ktlint:standard:function-naming")
fun SourceType(row: ResultRow): SourceType? {
    val catalogueSource =
        GetCatalogueSource
            .getCatalogueSourceOrNull(row[SourceTable.id].value)
            ?: return null
    val sourceExtension =
        if (row.hasValue(ExtensionTable.id)) {
            row
        } else {
            ExtensionTable
                .select { ExtensionTable.id eq row[SourceTable.extension] }
                .first()
        }

    return SourceType(row, sourceExtension, catalogueSource)
}

data class SourceNodeList(
    override val nodes: List<SourceType>,
    override val edges: List<SourceEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class SourceEdge(
        override val cursor: Cursor,
        override val node: SourceType,
    ) : Edge()

    companion object {
        fun List<SourceType>.toNodeList(): SourceNodeList {
            return SourceNodeList(
                nodes = this,
                edges = getEdges(),
                pageInfo =
                    PageInfo(
                        hasNextPage = false,
                        hasPreviousPage = false,
                        startCursor = Cursor(0.toString()),
                        endCursor = Cursor(lastIndex.toString()),
                    ),
                totalCount = size,
            )
        }

        private fun List<SourceType>.getEdges(): List<SourceEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                SourceEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                SourceEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}

sealed interface Filter

data class HeaderFilter(val name: String) : Filter

data class SeparatorFilter(val name: String) : Filter

data class SelectFilter(val name: String, val values: List<String>, val default: Int) : Filter

data class TextFilter(val name: String, val default: String) : Filter

data class CheckBoxFilter(val name: String, val default: Boolean) : Filter

enum class TriState {
    IGNORE,
    INCLUDE,
    EXCLUDE,
}

data class TriStateFilter(val name: String, val default: TriState) : Filter

data class SortFilter(val name: String, val values: List<String>, val default: SortSelection?) : Filter {
    data class SortSelection(val index: Int, val ascending: Boolean) {
        constructor(selection: SourceFilter.Sort.Selection) :
            this(selection.index, selection.ascending)
    }
}

data class GroupFilter(val name: String, val filters: List<Filter>) : Filter

fun filterOf(filter: SourceFilter<*>): Filter {
    return when (filter) {
        is SourceFilter.Header -> HeaderFilter(filter.name)
        is SourceFilter.Separator -> SeparatorFilter(filter.name)
        is SourceFilter.Select<*> -> SelectFilter(filter.name, filter.displayValues, filter.state)
        is SourceFilter.Text -> TextFilter(filter.name, filter.state)
        is SourceFilter.CheckBox -> CheckBoxFilter(filter.name, filter.state)
        is SourceFilter.TriState ->
            TriStateFilter(
                filter.name,
                when (filter.state) {
                    SourceFilter.TriState.STATE_INCLUDE -> TriState.INCLUDE
                    SourceFilter.TriState.STATE_EXCLUDE -> TriState.EXCLUDE
                    else -> TriState.IGNORE
                },
            )
        is SourceFilter.Group<*> ->
            GroupFilter(
                filter.name,
                filter.state.map { filterOf(it as SourceFilter<*>) },
            )
        is SourceFilter.Sort -> SortFilter(filter.name, filter.values.asList(), filter.state?.let(SortFilter::SortSelection))
        else -> throw RuntimeException("sealed class cannot have more subtypes!")
    }
}

/*sealed interface FilterChange {
    val position: Int
}

data class GroupFilterChange(
    override val position: Int,
    val filter: FilterChange
) : FilterChange

data class TriStateFilterChange(
    override val position: Int,
    val state: TriState
) : FilterChange

data class CheckBoxFilterChange(
    override val position: Int,
    val state: Boolean
) : FilterChange

data class SelectFilterChange(
    override val position: Int,
    val state: Int
) : FilterChange

data class TextFilterChange(
    override val position: Int,
    val state: String
) : FilterChange

data class SortFilterChange(
    override val position: Int,
    val state: SortFilter.SortSelection
) : FilterChange

private inline fun <reified T> filterChangeAs(filterChange: FilterChange): T {
    return filterChange as? T ?: throw Exception("Expected ${T::class.simpleName}, found ${filterChange::class.simpleName}")
}*/

data class FilterChange(
    val position: Int,
    val selectState: Int? = null,
    val textState: String? = null,
    val checkBoxState: Boolean? = null,
    val triState: TriState? = null,
    val sortState: SortFilter.SortSelection? = null,
    val groupChange: FilterChange? = null,
)

fun updateFilterList(
    source: CatalogueSource,
    changes: List<FilterChange>?,
): FilterList {
    val filterList = source.getFilterList()

    changes?.forEach { change ->
        when (val filter = filterList[change.position]) {
            is SourceFilter.Header -> {
                // NOOP
            }
            is SourceFilter.Separator -> {
                // NOOP
            }
            is SourceFilter.Select<*> -> {
                filter.state = change.selectState
                    ?: throw Exception("Expected select state change at position ${change.position}")
            }
            is SourceFilter.Text -> {
                filter.state = change.textState
                    ?: throw Exception("Expected text state change at position ${change.position}")
            }
            is SourceFilter.CheckBox -> {
                filter.state = change.checkBoxState
                    ?: throw Exception("Expected checkbox state change at position ${change.position}")
            }
            is SourceFilter.TriState -> {
                filter.state = change.triState?.ordinal
                    ?: throw Exception("Expected tri state change at position ${change.position}")
            }
            is SourceFilter.Group<*> -> {
                val groupChange =
                    change.groupChange
                        ?: throw Exception("Expected group change at position ${change.position}")

                when (val groupFilter = filter.state[groupChange.position]) {
                    is SourceFilter.CheckBox -> {
                        groupFilter.state = groupChange.checkBoxState
                            ?: throw Exception("Expected checkbox state change at position ${change.position}")
                    }
                    is SourceFilter.TriState -> {
                        groupFilter.state = groupChange.triState?.ordinal
                            ?: throw Exception("Expected tri state change at position ${change.position}")
                    }
                    is SourceFilter.Text -> {
                        groupFilter.state = groupChange.textState
                            ?: throw Exception("Expected text state change at position ${change.position}")
                    }
                    is SourceFilter.Select<*> -> {
                        groupFilter.state = groupChange.selectState
                            ?: throw Exception("Expected select state change at position ${change.position}")
                    }
                }
            }
            is SourceFilter.Sort -> {
                filter.state = change.sortState?.run {
                    SourceFilter.Sort.Selection(index, ascending)
                } ?: throw Exception("Expected sort state change at position ${change.position}")
            }
        }
    }
    return filterList
}

sealed interface Preference

data class SwitchPreference(
    val key: String,
    val title: String,
    val summary: String?,
    val visible: Boolean,
    val currentValue: Boolean?,
    val default: Boolean,
) : Preference

data class CheckBoxPreference(
    val key: String,
    val title: String,
    val summary: String?,
    val visible: Boolean,
    val currentValue: Boolean?,
    val default: Boolean,
) : Preference

data class EditTextPreference(
    val key: String,
    val title: String?,
    val summary: String?,
    val visible: Boolean,
    val currentValue: String?,
    val default: String?,
    val dialogTitle: String?,
    val dialogMessage: String?,
    val text: String?,
) : Preference

data class ListPreference(
    val key: String,
    val title: String?,
    val summary: String?,
    val visible: Boolean,
    val currentValue: String?,
    val default: String?,
    val entries: List<String>,
    val entryValues: List<String>,
) : Preference

data class MultiSelectListPreference(
    val key: String,
    val title: String?,
    val summary: String?,
    val visible: Boolean,
    val currentValue: List<String>?,
    val default: List<String>?,
    val dialogTitle: String?,
    val dialogMessage: String?,
    val entries: List<String>,
    val entryValues: List<String>,
) : Preference

fun preferenceOf(preference: SourcePreference): Preference {
    return when (preference) {
        is SourceSwitchPreference ->
            SwitchPreference(
                preference.key,
                preference.title.toString(),
                preference.summary?.toString(),
                preference.visible,
                preference.currentValue as Boolean,
                preference.defaultValue as Boolean,
            )
        is SourceCheckBoxPreference ->
            CheckBoxPreference(
                preference.key,
                preference.title.toString(),
                preference.summary?.toString(),
                preference.visible,
                preference.currentValue as Boolean,
                preference.defaultValue as Boolean,
            )
        is SourceEditTextPreference ->
            EditTextPreference(
                preference.key,
                preference.title?.toString(),
                preference.summary?.toString(),
                preference.visible,
                (preference.currentValue as CharSequence?)?.toString(),
                (preference.defaultValue as CharSequence?)?.toString(),
                preference.dialogTitle?.toString(),
                preference.dialogMessage?.toString(),
                preference.text,
            )
        is SourceListPreference ->
            ListPreference(
                preference.key,
                preference.title?.toString(),
                preference.summary?.toString(),
                preference.visible,
                (preference.currentValue as CharSequence?)?.toString(),
                (preference.defaultValue as CharSequence?)?.toString(),
                preference.entries.map { it.toString() },
                preference.entryValues.map { it.toString() },
            )
        is SourceMultiSelectListPreference ->
            MultiSelectListPreference(
                preference.key,
                preference.title?.toString(),
                preference.summary?.toString(),
                preference.visible,
                (preference.currentValue as Collection<*>?)?.map { it.toString() },
                (preference.defaultValue as Collection<*>?)?.map { it.toString() },
                preference.dialogTitle?.toString(),
                preference.dialogMessage?.toString(),
                preference.entries.map { it.toString() },
                preference.entryValues.map { it.toString() },
            )
        else -> throw RuntimeException("sealed class cannot have more subtypes!")
    }
}
