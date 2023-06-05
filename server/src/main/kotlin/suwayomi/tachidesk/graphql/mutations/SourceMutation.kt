package suwayomi.tachidesk.graphql.mutations

import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.graphql.types.PreferenceObject
import suwayomi.tachidesk.manga.impl.MangaList.insertOrGet
import suwayomi.tachidesk.manga.impl.Search
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class SourceMutation {

    enum class FetchSourceMangaType {
        SEARCH,
        POPULAR,
        LATEST
    }
    data class FilterChange(
        val position: Int,
        val state: String
    )
    data class FetchSourceMangaInput(
        val clientMutationId: String? = null,
        val source: Long,
        val type: FetchSourceMangaType,
        val page: Int,
        val query: String? = null,
        val filters: List<FilterChange>? = null
    )
    data class FetchSourceMangaPayload(
        val clientMutationId: String?,
        val mangas: List<MangaType>,
        val hasNextPage: Boolean
    )

    fun fetchSourceManga(
        input: FetchSourceMangaInput
    ): CompletableFuture<FetchSourceMangaPayload> {
        val (clientMutationId, sourceId, type, page, query, filters) = input

        return future {
            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)!!
            val mangasPage = when (type) {
                FetchSourceMangaType.SEARCH -> {
                    source.fetchSearchManga(
                        page = page,
                        query = query.orEmpty(),
                        filters = Search.buildFilterList(
                            sourceId = sourceId,
                            changes = filters?.map { Search.FilterChange(it.position, it.state) }
                                .orEmpty()
                        )
                    ).awaitSingle()
                }
                FetchSourceMangaType.POPULAR -> {
                    source.fetchPopularManga(page).awaitSingle()
                }
                FetchSourceMangaType.LATEST -> {
                    if (!source.supportsLatest) throw Exception("Source does not support latest")
                    source.fetchLatestUpdates(page).awaitSingle()
                }
            }

            val mangaIds = mangasPage.insertOrGet(sourceId)

            val mangas = transaction {
                MangaTable.select { MangaTable.id inList mangaIds }
                    .map { MangaType(it) }
            }.sortedBy {
                mangaIds.indexOf(it.id)
            }

            FetchSourceMangaPayload(
                clientMutationId = clientMutationId,
                mangas = mangas,
                hasNextPage = mangasPage.hasNextPage
            )
        }
    }

    data class SourcePreferenceChange(
        val position: Int,
        val state: String
    )
    data class UpdateSourcePreferenceInput(
        val clientMutationId: String? = null,
        val source: Long,
        val change: SourcePreferenceChange
    )
    data class UpdateSourcePreferencePayload(
        val clientMutationId: String?,
        val preferences: List<PreferenceObject>
    )

    fun updateSourcePreference(
        input: UpdateSourcePreferenceInput
    ): UpdateSourcePreferencePayload {
        val (clientMutationId, sourceId, change) = input

        Source.setSourcePreference(sourceId, Source.SourcePreferenceChange(change.position, change.state))

        return UpdateSourcePreferencePayload(
            clientMutationId = clientMutationId,
            preferences = Source.getSourcePreferences(sourceId).map { PreferenceObject(it.type, it.props) }
        )
    }
}
