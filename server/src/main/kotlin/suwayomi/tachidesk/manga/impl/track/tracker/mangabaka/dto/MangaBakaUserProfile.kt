package suwayomi.tachidesk.manga.impl.track.tracker.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaBakaUserProfileResponse(
    val data: MangaBakaUserProfile,
)

@Serializable
data class MangaBakaUserProfile(
    // incomplete DTO since this is the only part we need
    val id: String,
    @SerialName("rating_steps")
    val ratingSteps: Int,
    val nickname: String?,
    @SerialName("preferred_username")
    val preferredUsername: String?,
)
