package suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALRelatedResult(
    val data: ALRelatedData,
)

@Serializable
data class ALRelatedData(
    val Media: ALRelatedMedia?,
)

@Serializable
data class ALRelatedMedia(
    val relations: ALRelationConnection = ALRelationConnection(),
    val recommendations: ALRecommendationConnection = ALRecommendationConnection(),
)

@Serializable
data class ALRelationConnection(
    val edges: List<ALRelationEdge> = emptyList(),
)

@Serializable
data class ALRelationEdge(
    val relationType: String? = null,
    val node: ALRelatedNode?,
)

@Serializable
data class ALRecommendationConnection(
    val edges: List<ALRecommendationEdge> = emptyList(),
)

@Serializable
data class ALRecommendationEdge(
    val node: ALRecommendationNode?,
)

@Serializable
data class ALRecommendationNode(
    val mediaRecommendation: ALRelatedNode?,
)

@Serializable
data class ALRelatedNode(
    val id: Long,
    val type: String? = null,
    val title: ALItemTitle,
    val coverImage: ItemCover,
)
