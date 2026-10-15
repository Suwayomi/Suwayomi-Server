package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALListItem(
    @SerialName("num_chapters")
    val numChapters: Int = 0,
    @SerialName("my_list_status")
    val myListStatus: MALListItemStatus? = null,
)

@Serializable
// Every field here is optional in the MAL schema; missing fields used to blow
// up decoding with MissingFieldException -> 500.
data class MALListItemStatus(
    @SerialName("is_rereading")
    val isRereading: Boolean = false,
    val status: String = "",
    @SerialName("num_chapters_read")
    val numChaptersRead: Double = 0.0,
    val score: Int = 0,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("finish_date")
    val finishDate: String? = null,
)
