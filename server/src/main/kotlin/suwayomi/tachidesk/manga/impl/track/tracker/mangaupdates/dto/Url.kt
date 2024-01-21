package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.Serializable

@Serializable
data class Url(
    val original: String? = null,
    val thumb: String? = null,
)
