/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import graphql.execution.DataFetcherResult
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.MangaRecommendations
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class MangaRecommendationsQuery {
    data class RecommendationEntry(
        val anilistId: Int,
        val title: String,
        val coverUrl: String?,
        val description: String?,
        val genres: List<String>,
        val averageScore: Int?,
        val rating: Int?,
        val anilistUrl: String?,
        val sourceMangaId: Int?,
        val sourceMangaTitle: String?,
    )

    private fun MangaRecommendations.Recommendation.toEntry() =
        RecommendationEntry(
            anilistId = anilistId,
            title = title,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            averageScore = averageScore,
            rating = rating,
            anilistUrl = anilistUrl,
            sourceMangaId = sourceMangaId,
            sourceMangaTitle = sourceMangaTitle,
        )

    @RequireAuth
    fun mangaRecommendations(mangaId: Int): CompletableFuture<DataFetcherResult<List<RecommendationEntry>?>> =
        future {
            asDataFetcherResult { MangaRecommendations.forManga(mangaId).map { it.toEntry() } }
        }

    @RequireAuth
    fun libraryRecommendations(
        perMangaLimit: Int? = null,
        totalLimit: Int? = null,
    ): CompletableFuture<DataFetcherResult<List<RecommendationEntry>?>> =
        future {
            asDataFetcherResult {
                MangaRecommendations
                    .fromLibrary(
                        perMangaLimit = perMangaLimit?.coerceIn(1, 25) ?: 5,
                        totalLimit = totalLimit?.coerceIn(1, 200) ?: 50,
                    ).map { it.toEntry() }
            }
        }
}
