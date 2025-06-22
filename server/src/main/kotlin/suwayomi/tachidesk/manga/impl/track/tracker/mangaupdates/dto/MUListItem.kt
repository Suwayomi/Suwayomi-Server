package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.MangaUpdates.Companion.READING_LIST
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
        this.status = listId ?: READING_LIST
        this.last_chapter_read = this@copyTo.status?.chapter?.toDouble() ?: 0.0
    }
