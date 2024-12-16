package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.Serializable

@Serializable
data class Image(
    val url: Url? = null,
    val height: Int? = null,
    val width: Int? = null,
)
