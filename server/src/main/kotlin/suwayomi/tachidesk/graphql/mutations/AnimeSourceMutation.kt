package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.anime.impl.AnimeList.insertOrUpdate
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.AnimeType
import suwayomi.tachidesk.graphql.types.FilterChange
import suwayomi.tachidesk.server.JavalinSetup.future
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter as SourceFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import java.util.concurrent.CompletableFuture

class AnimeSourceMutation {
    enum class FetchSourceAnimeType {
        SEARCH,
        POPULAR,
        LATEST,
    }

    data class FetchSourceAnimeInput(
        val clientMutationId: String? = null,
        val source: String,
        val type: FetchSourceAnimeType,
        val page: Int,
        val query: String? = null,
        val filters: List<FilterChange>? = null,
    )

    data class FetchSourceAnimePayload(
        val clientMutationId: String?,
        val animes: List<AnimeType>,
        val hasNextPage: Boolean,
    )

    @RequireAuth
    fun fetchSourceAnime(input: FetchSourceAnimeInput): CompletableFuture<DataFetcherResult<FetchSourceAnimePayload?>> {
        val (clientMutationId, sourceIdRaw, type, page, query, filters) = input
        val sourceId = sourceIdRaw.toLong()

        return future {
            asDataFetcherResult {
                val source = GetAnimeCatalogueSource.getCatalogueSourceOrNull(sourceId)!!
                val animesPage =
                    when (type) {
                        FetchSourceAnimeType.SEARCH -> {
                            source.getSearchAnime(
                                page = page,
                                query = query.orEmpty(),
                                filters = updateAnimeFilterList(source, filters),
                            )
                        }

                        FetchSourceAnimeType.POPULAR -> source.getPopularAnime(page)

                        FetchSourceAnimeType.LATEST -> {
                            if (!source.supportsLatest) throw Exception("Source does not support latest")
                            source.getLatestUpdates(page)
                        }
                    }

                val animeIds = animesPage.insertOrUpdate(sourceId)
                val animes =
                    transaction {
                        AnimeTable
                            .selectAll()
                            .where { AnimeTable.id inList animeIds }
                            .map { AnimeType(it) }
                    }.sortedBy { animeIds.indexOf(it.id) }

                FetchSourceAnimePayload(
                    clientMutationId = clientMutationId,
                    animes = animes,
                    hasNextPage = animesPage.hasNextPage,
                )
            }
        }
    }
}

private fun updateAnimeFilterList(
    source: AnimeCatalogueSource,
    changes: List<FilterChange>?,
): AnimeFilterList {
    val filterList = source.getFilterList()

    changes?.forEach { change ->
        when (val filter = filterList[change.position]) {
            is SourceFilter.Header -> {}
            is SourceFilter.Separator -> {}
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
                val groupChange = change.groupChange ?: throw Exception("Expected group change at position ${change.position}")
                val groupFilter = filter.state[groupChange.position]
                when (groupFilter) {
                    is SourceFilter.CheckBox ->
                        groupFilter.state =
                            groupChange.checkBoxState
                                ?: throw Exception("Expected checkbox state change at position ${groupChange.position}")
                    is SourceFilter.TriState ->
                        groupFilter.state =
                            groupChange.triState?.ordinal
                                ?: throw Exception("Expected tri state change at position ${groupChange.position}")
                    is SourceFilter.Text ->
                        groupFilter.state =
                            groupChange.textState
                                ?: throw Exception("Expected text state change at position ${groupChange.position}")
                    is SourceFilter.Select<*> ->
                        groupFilter.state =
                            groupChange.selectState
                                ?: throw Exception("Expected select state change at position ${groupChange.position}")
                    is SourceFilter.Sort ->
                        groupFilter.state =
                            groupChange.sortState?.let { SourceFilter.Sort.Selection(it.index, it.ascending) }
                                ?: throw Exception("Expected sort state change at position ${groupChange.position}")
                }
            }
            is SourceFilter.Sort -> {
                filter.state =
                    change.sortState?.let { SourceFilter.Sort.Selection(it.index, it.ascending) }
                        ?: throw Exception("Expected sort state change at position ${change.position}")
            }
        }
    }

    return filterList
}
