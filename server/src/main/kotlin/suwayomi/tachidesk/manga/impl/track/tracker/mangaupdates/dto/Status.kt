package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.Serializable

@Serializable
data class Status(
    val volume: Int? = null,
    val chapter: Int? = null,
)
