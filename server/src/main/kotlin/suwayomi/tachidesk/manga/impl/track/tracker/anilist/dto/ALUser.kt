package suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALCurrentUserResult(
    val data: ALUserViewer? = null,
)

@Serializable
data class ALUserViewer(
    @SerialName("Viewer")
    val viewer: ALUserViewerData? = null,
)

@Serializable
data class ALUserViewerData(
    val id: Int,
    val mediaListOptions: ALUserListOptions? = null,
)

@Serializable
data class ALUserListOptions(
    val scoreFormat: String? = null,
)
