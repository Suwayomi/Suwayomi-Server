package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.Serializable

@Serializable
data class Series(
    val id: Long? = null,
    val title: String? = null,
)
