package suwayomi.tachidesk.graphql.mutations

import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.source.model.SManga
import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.MangaList
import suwayomi.tachidesk.manga.impl.metadata.AniListMetadataProvider
import suwayomi.tachidesk.manga.impl.metadata.MangaUpdatesMetadataProvider
import suwayomi.tachidesk.manga.impl.metadata.MetadataProvider
import suwayomi.tachidesk.manga.impl.util.getThumbnailDownloadPath
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.JavalinSetup.future
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

class MetadataMutation {
    private val applicationDirs: ApplicationDirs by injectLazy()

    private val providers: Map<String, MetadataProvider> =
        listOf(
            AniListMetadataProvider(),
            MangaUpdatesMetadataProvider(),
        ).associateBy { it.name.lowercase() }

    // --- Search ---

    data class MetadataSearchResultType(
        val externalId: String,
        val title: String,
        val author: String?,
        val coverUrl: String?,
        val year: Int?,
        val description: String?,
    )

    data class SearchMetadataProviderInput(
        val clientMutationId: String? = null,
        val provider: String,
        val query: String,
        val author: String? = null,
    )

    data class SearchMetadataProviderPayload(
        val clientMutationId: String?,
        val results: List<MetadataSearchResultType>,
    )

    @RequireAuth
    fun searchMetadataProvider(input: SearchMetadataProviderInput): CompletableFuture<DataFetcherResult<SearchMetadataProviderPayload?>> {
        val (clientMutationId, providerName, query, author) = input

        return future {
            asDataFetcherResult {
                val provider =
                    providers[providerName.lowercase()]
                        ?: throw IllegalArgumentException(
                            "Unknown provider '$providerName'. Available: ${providers.keys.joinToString()}",
                        )

                val results =
                    provider.search(query, author).map {
                        MetadataSearchResultType(
                            externalId = it.externalId,
                            title = it.title,
                            author = it.author,
                            coverUrl = it.coverUrl,
                            year = it.year,
                            description = it.description,
                        )
                    }

                SearchMetadataProviderPayload(
                    clientMutationId = clientMutationId,
                    results = results,
                )
            }
        }
    }

    // --- Apply Match ---

    data class ApplyMetadataMatchInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
        val provider: String,
        val externalId: String,
        val includeCover: Boolean = true,
    )

    data class ApplyMetadataMatchPayload(
        val clientMutationId: String?,
        val manga: MangaType,
    )

    @RequireAuth
    fun applyMetadataMatch(input: ApplyMetadataMatchInput): CompletableFuture<DataFetcherResult<ApplyMetadataMatchPayload?>> {
        val (clientMutationId, mangaId, providerName, externalId, includeCover) = input

        return future {
            asDataFetcherResult {
                val provider =
                    providers[providerName.lowercase()]
                        ?: throw IllegalArgumentException(
                            "Unknown provider '$providerName'. Available: ${providers.keys.joinToString()}",
                        )

                val details = provider.getDetails(externalId)

                val row =
                    transaction {
                        MangaTable.selectAll().where { MangaTable.id eq mangaId }.firstOrNull()
                            ?: throw IllegalArgumentException("Manga with id $mangaId not found")
                    }

                // Step 1: Update DB with text metadata
                transaction {
                    MangaTable.update({ MangaTable.id eq mangaId }) { update ->
                        details.title?.let { update[title] = it }
                        details.author?.let { update[author] = it }
                        details.artist?.let { update[artist] = it }
                        details.description?.let { update[description] = it }
                        details.genre?.let { update[genre] = it.joinToString(", ") }
                        details.status?.let { update[status] = it }
                    }
                }

                // Step 2: Download and save cover if requested
                val sourceId = row[MangaTable.sourceReference]
                if (includeCover && details.coverUrl != null) {
                    downloadAndSaveCover(mangaId, details.coverUrl, sourceId, row[MangaTable.url])
                }

                // Step 4: Return updated manga
                val manga =
                    transaction {
                        MangaType(MangaTable.selectAll().where { MangaTable.id eq mangaId }.first())
                    }

                ApplyMetadataMatchPayload(
                    clientMutationId = clientMutationId,
                    manga = manga,
                )
            }
        }
    }

    private suspend fun downloadAndSaveCover(
        mangaId: Int,
        coverUrl: String,
        sourceId: Long,
        mangaUrl: String,
    ) {
        val imageBytes =
            URI(coverUrl).toURL().openStream().use { it.readBytes() }

        // Clear old cache and save new thumbnail
        Manga.clearThumbnail(mangaId)
        val thumbnailDir = applicationDirs.thumbnailDownloadsRoot
        File(thumbnailDir).let { if (!it.exists()) it.mkdir() }
        ImageResponse.saveImage(
            getThumbnailDownloadPath(mangaId),
            imageBytes.inputStream(),
            null,
        )

        // Update DB
        transaction {
            MangaTable.update({ MangaTable.id eq mangaId }) { update ->
                update[thumbnail_url] = MangaList.proxyThumbnailUrl(mangaId)
                update[thumbnailUrlLastFetched] = System.currentTimeMillis()
            }
        }

        // Write cover.jpg for local source
        if (sourceId == LocalSource.ID) {
            val fileSystem = LocalSourceFileSystem(applicationDirs)
            val coverManager = LocalCoverManager(fileSystem)
            val sManga = SManga.create().apply { url = mangaUrl }
            coverManager.update(sManga, imageBytes.inputStream())
        }
    }
}
