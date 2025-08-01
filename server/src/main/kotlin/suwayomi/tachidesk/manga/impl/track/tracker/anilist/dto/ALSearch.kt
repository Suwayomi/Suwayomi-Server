package suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALSearchResult(
    val data: ALSearchPage,
)

@Serializable
data class ALSearchPage(
    @SerialName("Page")
    val page: ALSearchMedia,
)

@Serializable
data class ALSearchMedia(
    val media: List<ALSearchItem>,
)
