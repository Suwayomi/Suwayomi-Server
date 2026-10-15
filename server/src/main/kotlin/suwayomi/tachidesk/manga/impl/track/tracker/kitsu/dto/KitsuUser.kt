package suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuCurrentUserResult(
    val data: List<KitsuUser> = emptyList(),
)

@Serializable
data class KitsuUser(
    val id: String,
    val attributes: KitsuUserAttributes? = null,
)

@Serializable
data class KitsuUserAttributes(
    // the account's rating scale: simple/regular/advanced — decides which
    // ratingTwenty values the API accepts
    val ratingSystem: String? = null,
)
