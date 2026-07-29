package suwayomi.tachidesk.manga.impl.metadata

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import suwayomi.tachidesk.manga.impl.track.Track.htmlDecode
import uy.kohesive.injekt.injectLazy

class MangaUpdatesMetadataProvider : MetadataProvider {
    private val networkHelper: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    override val name: String = "MangaUpdates"

    override suspend fun search(
        query: String,
        author: String?,
    ): List<MetadataSearchResult> {
        val body =
            buildJsonObject {
                put("search", query)
            }.toString().toRequestBody(CONTENT_TYPE)

        val results =
            with(json) {
                networkHelper.client
                    .newCall(POST(url = "$BASE_URL/v1/series/search", body = body))
                    .awaitSuccess()
                    .parseAs<MUMetadataSearchResult>()
            }

        return results.results.map { item ->
            val record = item.record
            MetadataSearchResult(
                externalId = record.seriesId?.toString() ?: "",
                title = record.title?.htmlDecode() ?: "",
                author = null,
                coverUrl = record.image?.url?.original,
                year = record.year?.takeWhile { it.isDigit() }?.toIntOrNull(),
                description = record.description?.htmlDecode(),
            )
        }
    }

    override suspend fun getDetails(externalId: String): MetadataDetails {
        val series =
            with(json) {
                networkHelper.client
                    .newCall(GET("$BASE_URL/v1/series/$externalId"))
                    .awaitSuccess()
                    .parseAs<MUSeriesDetails>()
            }

        val authors =
            series.authors
                ?.filter { it.type.equals("Author", ignoreCase = true) }
                ?.mapNotNull { it.name }

        val artists =
            series.authors
                ?.filter { it.type.equals("Artist", ignoreCase = true) }
                ?.mapNotNull { it.name }

        return MetadataDetails(
            title = series.title?.htmlDecode(),
            author = authors?.joinToString(", ")?.takeIf { it.isNotEmpty() },
            artist = artists?.joinToString(", ")?.takeIf { it.isNotEmpty() },
            description = series.description?.htmlDecode(),
            genre = series.genres?.mapNotNull { it.genre }?.takeIf { it.isNotEmpty() },
            status = mapStatus(series.status),
            coverUrl = series.image?.url?.original,
        )
    }

    private fun mapStatus(status: String?): Int =
        when {
            status == null -> 0
            status.contains("Ongoing", ignoreCase = true) -> 1
            status.contains("Complete", ignoreCase = true) -> 2
            status.contains("Hiatus", ignoreCase = true) -> 6
            status.contains("Cancelled", ignoreCase = true) -> 5
            else -> 0
        }

    companion object {
        private const val BASE_URL = "https://api.mangaupdates.com"
        private val CONTENT_TYPE = "application/vnd.api+json".toMediaType()
    }
}

// --- MangaUpdates Response DTOs for metadata ---

@Serializable
data class MUMetadataSearchResult(
    val results: List<MUMetadataSearchResultItem> = emptyList(),
)

@Serializable
data class MUMetadataSearchResultItem(
    val record: MUMetadataRecord,
)

@Serializable
data class MUMetadataRecord(
    @SerialName("series_id")
    val seriesId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val image: MUMetadataImage? = null,
    val year: String? = null,
)

@Serializable
data class MUMetadataImage(
    val url: MUMetadataUrl? = null,
)

@Serializable
data class MUMetadataUrl(
    val original: String? = null,
)

@Serializable
data class MUSeriesDetails(
    @SerialName("series_id")
    val seriesId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val image: MUMetadataImage? = null,
    val authors: List<MUAuthor>? = null,
    val genres: List<MUGenre>? = null,
    val status: String? = null,
    val year: String? = null,
)

@Serializable
data class MUAuthor(
    val name: String? = null,
    val type: String? = null,
)

@Serializable
data class MUGenre(
    val genre: String? = null,
)
