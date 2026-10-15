package suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.ShikimoriApi

@Serializable
data class SMManga(
    val id: Long,
    val name: String = "",
    val chapters: Int? = null,
    val image: SUMangaCover? = null,
    val score: Double? = null,
    val url: String = "",
    val status: String = "",
    val kind: String = "",
    @SerialName("aired_on")
    val airedOn: String? = null,
) {
    fun toTrack(trackId: Int): TrackSearch =
        TrackSearch.create(trackId).apply {
            remote_id = this@SMManga.id
            title = name
            total_chapters = chapters ?: 0
            cover_url = image?.preview?.let { ShikimoriApi.BASE_URL + it }.orEmpty()
            summary = ""
            score = this@SMManga.score ?: 0.0
            tracking_url = ShikimoriApi.BASE_URL + url
            publishing_status = this@SMManga.status
            publishing_type = kind
            start_date = airedOn ?: ""
        }
}

@Serializable
data class SUMangaCover(
    val preview: String,
)
