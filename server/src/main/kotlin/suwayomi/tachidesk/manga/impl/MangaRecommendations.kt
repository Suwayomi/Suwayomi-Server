package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import uy.kohesive.injekt.injectLazy

object MangaRecommendations {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    private const val ANILIST_API = "https://graphql.anilist.co/"

    @Serializable
    private data class GqlMediaRecommendation(
        val id: Int,
        val title: AlTitle? = null,
        val coverImage: AlCoverImage? = null,
        val siteUrl: String? = null,
        val description: String? = null,
        val genres: List<String>? = null,
        val averageScore: Int? = null,
    )

    @Serializable private data class AlTitle(val romaji: String? = null, val english: String? = null)
    @Serializable private data class AlCoverImage(val large: String? = null, val medium: String? = null)

    @Serializable
    private data class GqlRecommendationNode(
        val rating: Int? = null,
        val mediaRecommendation: GqlMediaRecommendation? = null,
    )

    @Serializable private data class GqlRecommendations(val nodes: List<GqlRecommendationNode> = emptyList())
    @Serializable private data class GqlMedia(val recommendations: GqlRecommendations? = null)
    @Serializable private data class GqlData(val Media: GqlMedia? = null)
    @Serializable private data class GqlResponse(val data: GqlData? = null)

    data class Recommendation(
        val anilistId: Int,
        val title: String,
        val coverUrl: String?,
        val description: String?,
        val genres: List<String>,
        val averageScore: Int?,
        val rating: Int?,
        val anilistUrl: String?,
        /** ID of the manga in our library that this recommendation came from. */
        val sourceMangaId: Int? = null,
        val sourceMangaTitle: String? = null,
    )

    /**
     * Look up the AniList remote ID stored on the manga's track record (if any).
     */
    private fun anilistIdForManga(mangaId: Int): Long? =
        transaction {
            TrackRecordTable
                .selectAll()
                .where {
                    (TrackRecordTable.mangaId eq mangaId) and
                        (TrackRecordTable.trackerId eq TrackerManager.ANILIST)
                }.firstOrNull()
                ?.get(TrackRecordTable.remoteId)
        }

    private suspend fun fetchAnilistRecommendations(anilistId: Long): List<GqlRecommendationNode> {
        val query =
            """
            query (${'$'}mediaId: Int) {
                Media(id: ${'$'}mediaId, type: MANGA) {
                    recommendations(perPage: 25, sort: RATING_DESC) {
                        nodes {
                            rating
                            mediaRecommendation {
                                id
                                title { romaji english }
                                coverImage { large medium }
                                siteUrl
                                description
                                genres
                                averageScore
                            }
                        }
                    }
                }
            }
            """.trimIndent()
        val payload =
            buildJsonObject {
                put("query", query)
                putJsonObject("variables") { put("mediaId", anilistId) }
            }
        val response =
            runCatching {
                network.client
                    .newCall(POST(ANILIST_API, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
            }.onFailure { logger.warn(it) { "AniList recommendations request failed for id=$anilistId" } }
                .getOrNull() ?: return emptyList()
        val body =
            with(json) {
                runCatching { response.parseAs<GqlResponse>() }
                    .onFailure { logger.warn(it) { "AniList parse failed for id=$anilistId" } }
                    .getOrNull()
            } ?: return emptyList()
        return body.data?.Media?.recommendations?.nodes ?: emptyList()
    }

    private fun GqlRecommendationNode.toRecommendation(
        sourceMangaId: Int? = null,
        sourceMangaTitle: String? = null,
    ): Recommendation? {
        val media = mediaRecommendation ?: return null
        val title = media.title?.english ?: media.title?.romaji ?: return null
        return Recommendation(
            anilistId = media.id,
            title = title,
            coverUrl = media.coverImage?.large ?: media.coverImage?.medium,
            description = media.description?.replace(Regex("<[^>]+>"), "")?.trim(),
            genres = media.genres.orEmpty(),
            averageScore = media.averageScore,
            rating = rating,
            anilistUrl = media.siteUrl,
            sourceMangaId = sourceMangaId,
            sourceMangaTitle = sourceMangaTitle,
        )
    }

    /** Recommendations for a single manga. Empty list if the manga isn't tracked on AniList. */
    suspend fun forManga(mangaId: Int): List<Recommendation> {
        val anilistId = anilistIdForManga(mangaId) ?: return emptyList()
        return fetchAnilistRecommendations(anilistId).mapNotNull { it.toRecommendation() }
    }

    /**
     * Aggregate recommendations across the user's library. Walks at most
     * [perMangaLimit] AniList-tracked library mangas and returns the union
     * of their recommendations, deduplicated by AniList id.
     */
    suspend fun fromLibrary(
        perMangaLimit: Int = 5,
        totalLimit: Int = 50,
    ): List<Recommendation> {
        data class Seed(val mangaId: Int, val mangaTitle: String, val anilistId: Long)
        val seeds =
            transaction {
                (TrackRecordTable innerJoin MangaTable)
                    .selectAll()
                    .where {
                        (TrackRecordTable.trackerId eq TrackerManager.ANILIST) and
                            (MangaTable.inLibrary eq true)
                    }.limit(perMangaLimit)
                    .map {
                        Seed(
                            it[MangaTable.id].value,
                            it[MangaTable.title],
                            it[TrackRecordTable.remoteId],
                        )
                    }
            }
        if (seeds.isEmpty()) return emptyList()

        val seen = mutableSetOf<Int>()
        val out = mutableListOf<Recommendation>()
        for (seed in seeds) {
            val nodes = fetchAnilistRecommendations(seed.anilistId)
            for (node in nodes) {
                val rec = node.toRecommendation(seed.mangaId, seed.mangaTitle) ?: continue
                if (seen.add(rec.anilistId)) {
                    out += rec
                    if (out.size >= totalLimit) return out
                }
            }
        }
        return out
    }
}
