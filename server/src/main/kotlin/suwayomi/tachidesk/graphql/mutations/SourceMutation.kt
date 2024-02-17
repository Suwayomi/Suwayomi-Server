package suwayomi.tachidesk.graphql.mutations

import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.SwitchPreferenceCompat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.FilterChange
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.graphql.types.Preference
import suwayomi.tachidesk.graphql.types.SourceMetaType
import suwayomi.tachidesk.graphql.types.SourceType
import suwayomi.tachidesk.graphql.types.preferenceOf
import suwayomi.tachidesk.graphql.types.updateFilterList
import suwayomi.tachidesk.manga.impl.MangaList.insertOrGet
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
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

    fun setSourceMeta(input: SetSourceMetaInput): SetSourceMetaPayload {
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

    fun deleteSourceMeta(input: DeleteSourceMetaInput): DeleteSourceMetaPayload {
        val (clientMutationId, sourceId, key) = input

        val (meta, source) =
            transaction {
                val meta =
                    SourceMetaTable.select { (SourceMetaTable.ref eq sourceId) and (SourceMetaTable.key eq key) }
                        .firstOrNull()

                SourceMetaTable.deleteWhere { (SourceMetaTable.ref eq sourceId) and (SourceMetaTable.key eq key) }

                val source =
                    transaction {
                        SourceTable.select { SourceTable.id eq sourceId }.firstOrNull()
                            ?.let { SourceType(it) }
                    }

                if (meta != null) {
                    SourceMetaType(meta)
                } else {
                    null
                } to source
            }

        return DeleteSourceMetaPayload(clientMutationId, meta, source)
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

    fun fetchSourceManga(input: FetchSourceMangaInput): CompletableFuture<FetchSourceMangaPayload> {
        val (clientMutationId, sourceId, type, page, query, filters) = input

        return future {
            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)!!
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

            val mangaIds = mangasPage.insertOrGet(sourceId)

            val mangas =
                transaction {
                    MangaTable.select { MangaTable.id inList mangaIds }
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

    fun updateSourcePreference(input: UpdateSourcePreferenceInput): UpdateSourcePreferencePayload {
        val (clientMutationId, sourceId, change) = input

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

        return UpdateSourcePreferencePayload(
            clientMutationId = clientMutationId,
            preferences = Source.getSourcePreferencesRaw(sourceId).map { preferenceOf(it) },
            source =
                transaction {
                    SourceType(SourceTable.select { SourceTable.id eq sourceId }.first())!!
                },
        )
    }
}
