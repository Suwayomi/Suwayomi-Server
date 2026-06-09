package suwayomi.tachidesk.manga.impl.metadata

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy

class AniListMetadataProvider : MetadataProvider {
    private val networkHelper: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()
    private val logger = KotlinLogging.logger {}

    override val name: String = "AniList"

    override suspend fun search(
        query: String,
        author: String?,
    ): List<MetadataSearchResult> {
        val variables =
            buildJsonObject {
                put("search", query)
                put("type", "MANGA")
            }
        val body =
            buildJsonObject {
                put("query", SEARCH_QUERY)
                putJsonObject("variables") {
                    put("search", query)
                    put("type", "MANGA")
                }
            }.toString().toRequestBody(jsonMime)

        val response =
            with(json) {
                networkHelper.client
                    .newCall(POST(API_URL, body = body))
                    .awaitSuccess()
                    .parseAs<AniListSearchResponse>()
            }

        return response.data.page.media.map { media ->
            MetadataSearchResult(
                externalId = media.id.toString(),
                title = media.title.userPreferred ?: media.title.romaji ?: media.title.english ?: "",
                author =
                    media.staff
                        ?.edges
                        ?.firstOrNull { it.role.contains("Story", ignoreCase = true) }
                        ?.node
                        ?.name
                        ?.full,
                coverUrl = media.coverImage?.large ?: media.coverImage?.medium,
                year = media.startDate?.year,
                description = media.description?.stripHtml(),
            )
        }
    }

    override suspend fun getDetails(externalId: String): MetadataDetails {
        val body =
            buildJsonObject {
                put("query", DETAILS_QUERY)
                putJsonObject("variables") {
                    put("id", externalId.toInt())
                }
            }.toString().toRequestBody(jsonMime)

        val response =
            with(json) {
                networkHelper.client
                    .newCall(POST(API_URL, body = body))
                    .awaitSuccess()
                    .parseAs<AniListDetailsResponse>()
            }

        val media = response.data.media
        val staffEdges = media.staff?.edges.orEmpty()

        val author =
            staffEdges
                .firstOrNull { it.role.contains("Story", ignoreCase = true) }
                ?.node
                ?.name
                ?.full
        val artist =
            staffEdges
                .firstOrNull { it.role.contains("Art", ignoreCase = true) }
                ?.node
                ?.name
                ?.full

        return MetadataDetails(
            title = media.title.userPreferred ?: media.title.romaji ?: media.title.english,
            author = author,
            artist = artist,
            description = media.description?.stripHtml(),
            genre = media.genres?.takeIf { it.isNotEmpty() },
            status = mapStatus(media.status),
            coverUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
        )
    }

    private fun mapStatus(status: String?): Int =
        when (status) {
            "RELEASING" -> 1

            // ONGOING
            "FINISHED" -> 2

            // COMPLETED
            "HIATUS" -> 6

            // ON_HIATUS
            "CANCELLED" -> 5

            // CANCELLED
            "NOT_YET_RELEASED" -> 0

            // UNKNOWN
            else -> 0
        }

    private fun String.stripHtml(): String =
        this
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    companion object {
        private const val API_URL = "https://graphql.anilist.co"

        private val SEARCH_QUERY =
            """
            query (${'$'}search: String, ${'$'}type: MediaType) {
                Page(perPage: 10) {
                    media(search: ${'$'}search, type: ${'$'}type) {
                        id
                        title {
                            romaji
                            english
                            userPreferred
                        }
                        coverImage {
                            medium
                            large
                        }
                        startDate {
                            year
                        }
                        description(asHtml: false)
                        staff(perPage: 5) {
                            edges {
                                role
                                node {
                                    name {
                                        full
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()

        private val DETAILS_QUERY =
            """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id, type: MANGA) {
                    id
                    title {
                        romaji
                        english
                        userPreferred
                    }
                    coverImage {
                        medium
                        large
                        extraLarge
                    }
                    description(asHtml: false)
                    genres
                    status
                    staff(perPage: 10) {
                        edges {
                            role
                            node {
                                name {
                                    full
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()
    }
}

// --- AniList Response DTOs ---

@Serializable
data class AniListSearchResponse(
    val data: AniListSearchData,
)

@Serializable
data class AniListSearchData(
    @SerialName("Page") val page: AniListPage,
) {
    @Serializable
    data class AniListPage(
        val media: List<AniListMedia> = emptyList(),
    )
}

@Serializable
data class AniListDetailsResponse(
    val data: AniListDetailsData,
)

@Serializable
data class AniListDetailsData(
    @SerialName("Media") val media: AniListMedia,
) {
    constructor() : this(AniListMedia())
}

@Serializable
data class AniListMedia(
    val id: Int = 0,
    val title: AniListTitle = AniListTitle(),
    val coverImage: AniListCoverImage? = null,
    val startDate: AniListStartDate? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val status: String? = null,
    val staff: AniListStaffConnection? = null,
)

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val userPreferred: String? = null,
)

@Serializable
data class AniListCoverImage(
    val medium: String? = null,
    val large: String? = null,
    val extraLarge: String? = null,
)

@Serializable
data class AniListStartDate(
    val year: Int? = null,
)

@Serializable
data class AniListStaffConnection(
    val edges: List<AniListStaffEdge> = emptyList(),
)

@Serializable
data class AniListStaffEdge(
    val role: String = "",
    val node: AniListStaffNode? = null,
)

@Serializable
data class AniListStaffNode(
    val name: AniListStaffName? = null,
)

@Serializable
data class AniListStaffName(
    val full: String? = null,
)
