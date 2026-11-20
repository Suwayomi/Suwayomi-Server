package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALListItem(
    @SerialName("num_chapters")
    val numChapters: Int,
    @SerialName("my_list_status")
    val myListStatus: MALListItemStatus?,
)

@Serializable
data class MALListItemStatus(
    @SerialName("is_rereading")
    val isRereading: Boolean,
    val status: String,
    @SerialName("num_chapters_read")
    val numChaptersRead: Double,
    val score: Int,
    @SerialName("start_date")
    val startDate: String?,
    @SerialName("finish_date")
    val finishDate: String?,
)
