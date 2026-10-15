package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track

@Serializable
data class MUListItem(
    val series: MUSeries? = null,
    @SerialName("list_id")
    val listId: Int? = null,
    val status: MUStatus? = null,
    val priority: Int? = null,
)

fun MUListItem.copyTo(track: Track): Track =
    track.apply {
        // A missing list_id must not be rewritten as READING_LIST: keep the
        // local status when the remote does not report one.
        listId?.let { this.status = it }
        this.last_chapter_read = this@copyTo.status?.chapter ?: 0.0
        series?.title?.let { this.title = it }
        series?.id?.let { this.tracking_url = "https://www.mangaupdates.com/series.html?id=$it" }
    }
