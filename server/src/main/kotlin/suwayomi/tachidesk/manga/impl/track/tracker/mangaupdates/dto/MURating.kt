package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track

@Serializable
data class MURating(
    val rating: Double? = null,
)

fun MURating.copyTo(track: Track): Track =
    track.apply {
        this.score = rating ?: 0.0
    }
