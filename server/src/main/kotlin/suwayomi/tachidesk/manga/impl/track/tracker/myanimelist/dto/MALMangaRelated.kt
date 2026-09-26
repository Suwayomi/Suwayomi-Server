package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALMangaRelated(
    val id: Long,
    @SerialName("related_manga")
    val relatedManga: List<MALRelatedEdge> = emptyList(),
    val recommendations: List<MALRecommendationEdge> = emptyList(),
)

@Serializable
data class MALRelatedEdge(
    val node: MALRelatedNode,
    @SerialName("relation_type_formatted")
    val relationType: String? = null,
)

@Serializable
data class MALRecommendationEdge(
    val node: MALRelatedNode,
)

@Serializable
data class MALRelatedNode(
    val id: Long,
    val title: String,
    @SerialName("main_picture")
    val picture: MALRelatedPicture? = null,
) {
    val coverUrl: String
        get() = picture?.large?.takeIf { it.isNotBlank() } ?: picture?.medium.orEmpty()
}

@Serializable
data class MALRelatedPicture(
    val medium: String = "",
    val large: String = "",
)
