package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.anime.impl.Anime
import suwayomi.tachidesk.anime.impl.AnimeLibrary
import suwayomi.tachidesk.anime.impl.Episode
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.anime.model.table.EpisodeTable
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.AnimeType
import suwayomi.tachidesk.graphql.types.EpisodeType
import suwayomi.tachidesk.server.JavalinSetup.future
import java.time.Instant
import java.util.concurrent.CompletableFuture

class AnimeMutation {
    data class UpdateAnimePatch(
        val inLibrary: Boolean? = null,
    )

    data class UpdateAnimePayload(
        val clientMutationId: String?,
        val anime: AnimeType,
    )

    data class UpdateAnimeInput(
        val clientMutationId: String? = null,
        val id: Int,
        val patch: UpdateAnimePatch,
    )

    data class UpdateAnimesPayload(
        val clientMutationId: String?,
        val animes: List<AnimeType>,
    )

    data class UpdateAnimesInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
        val patch: UpdateAnimePatch,
    )

    private suspend fun updateAnimes(
        ids: List<Int>,
        patch: UpdateAnimePatch,
    ) {
        transaction {
            if (patch.inLibrary != null) {
                AnimeTable.update({ AnimeTable.id inList ids }) { update ->
                    patch.inLibrary.also {
                        update[inLibrary] = it
                        if (it) update[inLibraryAt] = Instant.now().epochSecond
                    }
                }
            }
        }.apply {
            if (patch.inLibrary == true) {
                ids.forEach { animeId ->
                    AnimeLibrary.addAnimeToLibrary(animeId)
                    Anime.fetchAnime(animeId)
                    Episode.fetchEpisodeList(animeId)
                }
            }
        }
    }

    @RequireAuth
    fun updateAnime(input: UpdateAnimeInput): CompletableFuture<DataFetcherResult<UpdateAnimePayload?>> {
        val (clientMutationId, id, patch) = input

        return future {
            asDataFetcherResult {
                updateAnimes(listOf(id), patch)

                val anime =
                    transaction {
                        AnimeType(AnimeTable.selectAll().where { AnimeTable.id eq id }.first())
                    }

                UpdateAnimePayload(
                    clientMutationId = clientMutationId,
                    anime = anime,
                )
            }
        }
    }

    @RequireAuth
    fun updateAnimes(input: UpdateAnimesInput): CompletableFuture<DataFetcherResult<UpdateAnimesPayload?>> {
        val (clientMutationId, ids, patch) = input

        return future {
            asDataFetcherResult {
                updateAnimes(ids, patch)

                val animes =
                    transaction {
                        AnimeTable.selectAll().where { AnimeTable.id inList ids }.map { AnimeType(it) }
                    }

                UpdateAnimesPayload(
                    clientMutationId = clientMutationId,
                    animes = animes,
                )
            }
        }
    }
    data class FetchAnimeInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class FetchAnimePayload(
        val clientMutationId: String?,
        val anime: AnimeType,
    )

    @RequireAuth
    fun fetchAnime(input: FetchAnimeInput): CompletableFuture<DataFetcherResult<FetchAnimePayload?>> {
        val (clientMutationId, id) = input

        return future {
            asDataFetcherResult {
                Anime.fetchAnime(id)

                val anime =
                    transaction {
                        AnimeTable.selectAll().where { AnimeTable.id eq id }.first()
                    }

                FetchAnimePayload(
                    clientMutationId = clientMutationId,
                    anime = AnimeType(anime),
                )
            }
        }
    }

    data class FetchEpisodesInput(
        val clientMutationId: String? = null,
        val animeId: Int,
    )

    data class FetchEpisodesPayload(
        val clientMutationId: String?,
        val episodes: List<EpisodeType>,
    )

    @RequireAuth
    fun fetchEpisodes(input: FetchEpisodesInput): CompletableFuture<DataFetcherResult<FetchEpisodesPayload?>> {
        val (clientMutationId, animeId) = input

        return future {
            asDataFetcherResult {
                Episode.fetchEpisodeList(animeId)

                val episodes =
                    transaction {
                        EpisodeTable
                            .selectAll()
                            .where { EpisodeTable.anime eq animeId }
                            .orderBy(EpisodeTable.sourceOrder)
                            .map { EpisodeType(it) }
                    }

                FetchEpisodesPayload(
                    clientMutationId = clientMutationId,
                    episodes = episodes,
                )
            }
        }
    }
}
