@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.SwitchPreferenceCompat
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.FilterChange
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.graphql.types.MetaInput
import suwayomi.tachidesk.graphql.types.Preference
import suwayomi.tachidesk.graphql.types.SourceMetaType
import suwayomi.tachidesk.graphql.types.SourceType
import suwayomi.tachidesk.graphql.types.preferenceOf
import suwayomi.tachidesk.graphql.types.updateFilterList
import suwayomi.tachidesk.manga.impl.MangaList.insertOrUpdate
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceMetaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class SourceMutation {
    data class SetSourceMetaInput(
        val clientMutationId: String? = null,
        val meta: SourceMetaType,
    )

    data class SetSourceMetaPayload(
        val clientMutationId: String?,
        val meta: SourceMetaType,
    )

    @RequireAuth
    fun setSourceMeta(input: SetSourceMetaInput): SetSourceMetaPayload? {
        val (clientMutationId, meta) = input

        Source.modifyMeta(meta.sourceId, meta.key, meta.value)

        return SetSourceMetaPayload(clientMutationId, meta)
    }

    data class DeleteSourceMetaInput(
        val clientMutationId: String? = null,
        val sourceId: Long,
        val key: String,
    )

    data class DeleteSourceMetaPayload(
        val clientMutationId: String?,
        val meta: SourceMetaType?,
        val source: SourceType?,
    )

    @RequireAuth
    fun deleteSourceMeta(input: DeleteSourceMetaInput): CompletableFuture<DeleteSourceMetaPayload?> {
        val (clientMutationId, sourceId, key) = input

        return future {
            val (meta, source) =
                suspendTransaction {
                    val meta =
                        SourceMetaTable
                            .selectAll()
                            .where { (SourceMetaTable.ref eq sourceId) and (SourceMetaTable.key eq key) }
                            .firstOrNull()

                    SourceMetaTable.deleteWhere { (SourceMetaTable.ref eq sourceId) and (SourceMetaTable.key eq key) }

                    val source =
                        SourceTable
                            .selectAll()
                            .where { SourceTable.id eq sourceId }
                            .firstOrNull()
                            ?.let { SourceType(it) }

                    if (meta != null) {
                        SourceMetaType(meta)
                    } else {
                        null
                    } to source
                }

            DeleteSourceMetaPayload(clientMutationId, meta, source)
        }
    }

    data class SetSourceMetasItem(
        val sourceIds: List<Long>,
        val metas: List<MetaInput>,
    )

    data class SetSourceMetasInput(
        val clientMutationId: String? = null,
        val items: List<SetSourceMetasItem>,
    )

    data class SetSourceMetasPayload(
        val clientMutationId: String?,
        val metas: List<SourceMetaType>,
        val sources: List<SourceType>,
    )

    @RequireAuth
    fun setSourceMetas(input: SetSourceMetasInput): CompletableFuture<SetSourceMetasPayload?> {
        val (clientMutationId, items) = input

        return future {
            val metaBySourceId =
                items
                    .flatMap { item ->
                        val metaMap = item.metas.associate { it.key to it.value }
                        item.sourceIds.map { sourceId -> sourceId to metaMap }
                    }.groupBy({ it.first }, { it.second })
                    .mapValues { (_, maps) -> maps.reduce { acc, map -> acc + map } }

            Source.modifySourceMetas(metaBySourceId)

            val allSourceIds = metaBySourceId.keys
            val allMetaKeys = metaBySourceId.values.flatMap { it.keys }.distinct()

            val (updatedMetas, sources) =
                suspendTransaction {
                    val updatedMetas =
                        SourceMetaTable
                            .selectAll()
                            .where { (SourceMetaTable.ref inList allSourceIds) and (SourceMetaTable.key inList allMetaKeys) }
                            .map { SourceMetaType(it) }

                    val sources =
                        SourceTable
                            .selectAll()
                            .where { SourceTable.id inList allSourceIds }
                            .mapNotNull { SourceType(it) }
                            .distinctBy { it.id }

                    updatedMetas to sources
                }

            SetSourceMetasPayload(clientMutationId, updatedMetas, sources)
        }
    }

    data class DeleteSourceMetasItem(
        val sourceIds: List<Long>,
        val keys: List<String>? = null,
        val prefixes: List<String>? = null,
    )

    data class DeleteSourceMetasInput(
        val clientMutationId: String? = null,
        val items: List<DeleteSourceMetasItem>,
    )

    data class DeleteSourceMetasPayload(
        val clientMutationId: String?,
        val metas: List<SourceMetaType>,
        val sources: List<SourceType>,
    )

    @RequireAuth
    fun deleteSourceMetas(input: DeleteSourceMetasInput): CompletableFuture<DeleteSourceMetasPayload?> {
        val (clientMutationId, items) = input

        return future {
            items.forEach { item ->
                require(!item.keys.isNullOrEmpty() || !item.prefixes.isNullOrEmpty()) {
                    "Either 'keys' or 'prefixes' must be provided for each item"
                }
            }

            val (allDeletedMetas, allSourceIds) =
                transaction {
                    val deletedMetas = mutableListOf<SourceMetaType>()
                    val sourceIds = mutableSetOf<Long>()

                    items.forEach { item ->
                        val keyCondition: Op<Boolean>? =
                            item.keys?.takeIf { it.isNotEmpty() }?.let { SourceMetaTable.key inList it }

                        val prefixCondition: Op<Boolean>? =
                            item.prefixes
                                ?.filter { it.isNotEmpty() }
                                ?.map { (SourceMetaTable.key like LikePattern("$it%")) as Op<Boolean> }
                                ?.reduceOrNull { acc, op -> acc or op }

                        val metaKeyCondition =
                            if (keyCondition != null && prefixCondition != null) {
                                keyCondition or prefixCondition
                            } else {
                                keyCondition ?: prefixCondition!!
                            }

                        val condition = (SourceMetaTable.ref inList item.sourceIds) and metaKeyCondition

                        deletedMetas +=
                            SourceMetaTable
                                .selectAll()
                                .where { condition }
                                .map { SourceMetaType(it) }

                        SourceMetaTable.deleteWhere { condition }
                        sourceIds += item.sourceIds
                    }

                    deletedMetas to sourceIds
                }

            val sources =
                suspendTransaction {
                    SourceTable
                        .selectAll()
                        .where { SourceTable.id inList allSourceIds }
                        .mapNotNull { SourceType(it) }
                        .distinctBy { it.id }
                }

            DeleteSourceMetasPayload(clientMutationId, allDeletedMetas, sources)
        }
    }

    enum class FetchSourceMangaType {
        SEARCH,
        POPULAR,
        LATEST,
    }

    data class FetchSourceMangaInput(
        val clientMutationId: String? = null,
        val source: Long,
        val type: FetchSourceMangaType,
        val page: Int,
        val query: String? = null,
        val filters: List<FilterChange>? = null,
    )

    data class FetchSourceMangaPayload(
        val clientMutationId: String?,
        val mangas: List<MangaType>,
        val hasNextPage: Boolean,
    )

    @RequireAuth
    fun fetchSourceManga(input: FetchSourceMangaInput): CompletableFuture<FetchSourceMangaPayload?> {
        val (clientMutationId, sourceId, type, page, query, filters) = input

        return future {
            val source = GetSource.getSourceOrNull(sourceId)!!
            val mangasPage =
                when (type) {
                    FetchSourceMangaType.SEARCH -> {
                        source.getSearchManga(
                            page = page,
                            query = query.orEmpty(),
                            filters = updateFilterList(source, filters),
                        )
                    }

                    FetchSourceMangaType.POPULAR -> {
                        source.getPopularManga(page)
                    }

                    FetchSourceMangaType.LATEST -> {
                        if (!source.supportsLatest) throw Exception("Source does not support latest")
                        source.getLatestUpdates(page)
                    }
                }

            val mangaIds = mangasPage.insertOrUpdate(sourceId)

            val mangas =
                transaction {
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id inList mangaIds }
                        .map { MangaType(it) }
                }.sortedBy {
                    mangaIds.indexOf(it.id)
                }

            FetchSourceMangaPayload(
                clientMutationId = clientMutationId,
                mangas = mangas,
                hasNextPage = mangasPage.hasNextPage,
            )
        }
    }

    data class SourcePreferenceChange(
        val position: Int,
        val switchState: Boolean? = null,
        val checkBoxState: Boolean? = null,
        val editTextState: String? = null,
        val listState: String? = null,
        val multiSelectState: List<String>? = null,
    )

    data class UpdateSourcePreferenceInput(
        val clientMutationId: String? = null,
        val source: Long,
        val change: SourcePreferenceChange,
    )

    data class UpdateSourcePreferencePayload(
        val clientMutationId: String?,
        val preferences: List<Preference>,
        val source: SourceType,
    )

    @RequireAuth
    fun updateSourcePreference(input: UpdateSourcePreferenceInput): CompletableFuture<UpdateSourcePreferencePayload?> {
        val (clientMutationId, sourceId, change) = input

        return future {
            Source.setSourcePreference(sourceId, change.position, "") { preference ->
                when (preference) {
                    is SwitchPreferenceCompat -> change.switchState
                    is CheckBoxPreference -> change.checkBoxState
                    is EditTextPreference -> change.editTextState
                    is ListPreference -> change.listState
                    is MultiSelectListPreference -> change.multiSelectState?.toSet()
                    else -> throw RuntimeException("sealed class cannot have more subtypes!")
                } ?: throw Exception("Expected change to ${preference::class.simpleName}")
            }

            UpdateSourcePreferencePayload(
                clientMutationId = clientMutationId,
                preferences = Source.getSourcePreferencesRaw(sourceId).map { preferenceOf(it) },
                source =
                    suspendTransaction {
                        SourceType(SourceTable.selectAll().where { SourceTable.id eq sourceId }.first())!!
                    },
            )
        }
    }
}
