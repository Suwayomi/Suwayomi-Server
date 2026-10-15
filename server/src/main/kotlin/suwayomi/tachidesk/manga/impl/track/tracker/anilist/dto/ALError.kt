package suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto

import kotlinx.serialization.Serializable

/**
 * AniList reports API-level failures as HTTP 200 with an `errors` array.
 * Mutations that used to read (or skip) the body unchecked silently "wrote"
 * nothing remotely.
 */
@Serializable
data class ALGraphQlErrors(
    val errors: List<ALError>? = null,
)

@Serializable
data class ALError(
    val message: String? = null,
    val status: Int? = null,
)
