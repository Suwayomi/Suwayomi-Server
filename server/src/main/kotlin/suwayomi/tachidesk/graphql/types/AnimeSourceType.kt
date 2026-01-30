/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import androidx.preference.CheckBoxPreference as SourceCheckBoxPreference
import androidx.preference.EditTextPreference as SourceEditTextPreference
import androidx.preference.ListPreference as SourceListPreference
import androidx.preference.MultiSelectListPreference as SourceMultiSelectListPreference
import androidx.preference.Preference as SourcePreference
import androidx.preference.SwitchPreferenceCompat as SourceSwitchPreference
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter as SourceFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import suwayomi.tachidesk.anime.impl.Source.getSourcePreferencesRaw
import suwayomi.tachidesk.anime.impl.extension.AnimeExtension
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.anime.model.dataclass.AnimeSourceDataClass
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.anime.model.table.AnimeSourceTable
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import java.util.concurrent.CompletableFuture

class AnimeSourceType(
    val id: Long,
    val name: String,
    val lang: String,
    val iconUrl: String,
    val supportsLatest: Boolean,
    val isConfigurable: Boolean,
    val isNsfw: Boolean,
    val displayName: String,
    val baseUrl: String?,
) : Node {
    constructor(source: AnimeSourceDataClass) : this(
        id = source.id.toLong(),
        name = source.name,
        lang = source.lang,
        iconUrl = source.iconUrl,
        supportsLatest = source.supportsLatest,
        isConfigurable = source.isConfigurable,
        isNsfw = source.isNsfw,
        displayName = source.displayName,
        baseUrl = source.baseUrl,
    )

    constructor(row: ResultRow, sourceExtension: ResultRow, catalogueSource: AnimeCatalogueSource) : this(
        id = row[AnimeSourceTable.id].value,
        name = row[AnimeSourceTable.name],
        lang = row[AnimeSourceTable.lang],
        iconUrl = AnimeExtension.getExtensionIconUrl(sourceExtension[AnimeExtensionTable.apkName]),
        supportsLatest = catalogueSource.supportsLatest,
        isConfigurable = catalogueSource is ConfigurableAnimeSource,
        isNsfw = row[AnimeSourceTable.isNsfw],
        displayName = catalogueSource.toString(),
        baseUrl = catalogueSource.runCatching { (catalogueSource as? AnimeHttpSource)?.baseUrl }.getOrNull(),
    )

    fun anime(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<AnimeNodeList> =
        dataFetchingEnvironment.getValueFromDataLoader<Long, AnimeNodeList>("AnimeForSourceDataLoader", id)

    fun extension(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<AnimeExtensionType?> =
        dataFetchingEnvironment.getValueFromDataLoader<Long, AnimeExtensionType?>("AnimeExtensionForSourceDataLoader", id)

    fun preferences(): List<AnimePreference> = getSourcePreferencesRaw(id).map { animePreferenceOf(it) }

    fun filters(): List<AnimeSourceFilter> = getCatalogueSourceOrStub(id).getFilterList().map { filterOf(it) }
}

@Suppress("ktlint:standard:function-naming")
fun AnimeSourceType(row: ResultRow): AnimeSourceType? {
    val catalogueSource =
        GetAnimeCatalogueSource
            .getCatalogueSourceOrNull(row[AnimeSourceTable.id].value)
            ?: return null
    val sourceExtension =
        if (row.hasValue(AnimeExtensionTable.id)) {
            row
        } else {
            AnimeExtensionTable
                .selectAll()
                .where { AnimeExtensionTable.id eq row[AnimeSourceTable.extension] }
                .first()
        }

    return AnimeSourceType(row, sourceExtension, catalogueSource)
}

data class AnimeSourceNodeList(
    override val nodes: List<AnimeSourceType>,
    override val edges: List<AnimeSourceEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class AnimeSourceEdge(
        override val cursor: Cursor,
        override val node: AnimeSourceType,
    ) : Edge()

    companion object {
        fun List<AnimeSourceType>.toNodeList(): AnimeSourceNodeList =
            AnimeSourceNodeList(
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

        private fun List<AnimeSourceType>.getEdges(): List<AnimeSourceEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                AnimeSourceEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                AnimeSourceEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}

sealed interface AnimeSourceFilter

data class AnimeHeaderFilter(
    val name: String,
) : AnimeSourceFilter

data class AnimeSeparatorFilter(
    val name: String,
) : AnimeSourceFilter

data class AnimeSelectFilter(
    val name: String,
    val values: List<String>,
    val default: Int,
) : AnimeSourceFilter

data class AnimeTextFilter(
    val name: String,
    val default: String,
) : AnimeSourceFilter

data class AnimeCheckBoxFilter(
    val name: String,
    val default: Boolean,
) : AnimeSourceFilter

enum class AnimeTriState {
    IGNORE,
    INCLUDE,
    EXCLUDE,
}

data class AnimeTriStateFilter(
    val name: String,
    val default: AnimeTriState,
) : AnimeSourceFilter

data class AnimeSortFilter(
    val name: String,
    val values: List<String>,
    val default: AnimeSortSelection?,
) : AnimeSourceFilter {
    data class AnimeSortSelection(
        val index: Int,
        val ascending: Boolean,
    ) {
        constructor(selection: SourceFilter.Sort.Selection) : this(selection.index, selection.ascending)
    }
}

data class AnimeGroupFilter(
    val name: String,
    val filters: List<AnimeSourceFilter>,
) : AnimeSourceFilter

fun filterOf(filter: SourceFilter<*>): AnimeSourceFilter =
    when (filter) {
        is SourceFilter.Header -> AnimeHeaderFilter(filter.name)
        is SourceFilter.Separator -> AnimeSeparatorFilter(filter.name)
        is SourceFilter.Select<*> -> AnimeSelectFilter(filter.name, filter.values.map { it.toString() }, filter.state)
        is SourceFilter.Text -> AnimeTextFilter(filter.name, filter.state)
        is SourceFilter.CheckBox -> AnimeCheckBoxFilter(filter.name, filter.state)
        is SourceFilter.TriState ->
            AnimeTriStateFilter(
                filter.name,
                when (filter.state) {
                    SourceFilter.TriState.STATE_INCLUDE -> AnimeTriState.INCLUDE
                    SourceFilter.TriState.STATE_EXCLUDE -> AnimeTriState.EXCLUDE
                    else -> AnimeTriState.IGNORE
                },
            )
        is SourceFilter.Group<*> -> AnimeGroupFilter(filter.name, filter.state.map { filterOf(it as SourceFilter<*>) })
        is SourceFilter.Sort -> AnimeSortFilter(filter.name, filter.values.asList(), filter.state?.let(AnimeSortFilter::AnimeSortSelection))
        else -> throw RuntimeException("sealed class cannot have more subtypes!")
    }

sealed interface AnimePreference

data class AnimeSwitchPreference(
    val key: String?,
    val title: String?,
    val summary: String?,
    val visible: Boolean,
    val enabled: Boolean,
    val currentValue: Boolean?,
    val default: Boolean,
) : AnimePreference

data class AnimeCheckBoxPreference(
    val key: String?,
    val title: String?,
    val summary: String?,
    val visible: Boolean,
    val enabled: Boolean,
    val currentValue: Boolean?,
    val default: Boolean,
) : AnimePreference

data class AnimeEditTextPreference(
    val key: String?,
    val title: String?,
    val summary: String?,
    val visible: Boolean,
    val enabled: Boolean,
    val currentValue: String?,
    val default: String?,
    val dialogTitle: String?,
    val dialogMessage: String?,
    val text: String?,
) : AnimePreference

data class AnimeListPreference(
    val key: String?,
    val title: String?,
    val summary: String?,
    val visible: Boolean,
    val enabled: Boolean,
    val currentValue: String?,
    val default: String?,
    val entries: List<String>,
    val entryValues: List<String>,
) : AnimePreference

data class AnimeMultiSelectListPreference(
    val key: String?,
    val title: String?,
    val summary: String?,
    val visible: Boolean,
    val enabled: Boolean,
    val currentValue: List<String>?,
    val default: List<String>?,
    val dialogTitle: String?,
    val dialogMessage: String?,
    val entries: List<String>,
    val entryValues: List<String>,
) : AnimePreference

fun animePreferenceOf(preference: SourcePreference): AnimePreference =
    when (preference) {
        is SourceSwitchPreference ->
            AnimeSwitchPreference(
                preference.key,
                preference.title?.toString(),
                preference.summary?.toString(),
                preference.visible,
                preference.isEnabled,
                preference.currentValue as Boolean,
                preference.defaultValue as Boolean,
            )
        is SourceCheckBoxPreference ->
            AnimeCheckBoxPreference(
                preference.key,
                preference.title?.toString(),
                preference.summary?.toString(),
                preference.visible,
                preference.isEnabled,
                preference.currentValue as Boolean,
                preference.defaultValue as Boolean,
            )
        is SourceEditTextPreference ->
            AnimeEditTextPreference(
                preference.key,
                preference.title?.toString(),
                preference.summary?.toString(),
                preference.visible,
                preference.isEnabled,
                (preference.currentValue as CharSequence?)?.toString(),
                (preference.defaultValue as CharSequence?)?.toString(),
                preference.dialogTitle?.toString(),
                preference.dialogMessage?.toString(),
                preference.text,
            )
        is SourceListPreference ->
            AnimeListPreference(
                preference.key,
                preference.title?.toString(),
                preference.summary?.toString(),
                preference.visible,
                preference.isEnabled,
                (preference.currentValue as CharSequence?)?.toString(),
                (preference.defaultValue as CharSequence?)?.toString(),
                preference.entries.map { it.toString() },
                preference.entryValues.map { it.toString() },
            )
        is SourceMultiSelectListPreference ->
            AnimeMultiSelectListPreference(
                preference.key,
                preference.title?.toString(),
                preference.summary?.toString(),
                preference.visible,
                preference.isEnabled,
                (preference.currentValue as Collection<*>?)?.map { it.toString() },
                (preference.defaultValue as Collection<*>?)?.map { it.toString() },
                preference.dialogTitle?.toString(),
                preference.dialogMessage?.toString(),
                preference.entries.map { it.toString() },
                preference.entryValues.map { it.toString() },
            )
        else -> throw RuntimeException("sealed class cannot have more subtypes!")
    }
