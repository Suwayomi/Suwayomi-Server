package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track

@Serializable
data class Rating(
    val rating: Float? = null,
)

fun Rating.copyTo(track: Track): Track =
    track.apply {
        this.score = rating ?: 0f
    }
