package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.Track.htmlDecode
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch

@Serializable
data class MURecord(
    @SerialName("series_id")
    val seriesId: Long? = null,
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
    val image: MUImage? = null,
    val type: String? = null,
    val year: String? = null,
    @SerialName("latest_chapter")
    val latestChapter: Int? = null,
)

fun MURecord.toTrackSearch(id: Int): TrackSearch? {
    // A record without a series id can never be bound; drop it instead of
    // binding to the sentinel remote_id=0.
    val seriesId = seriesId ?: return null
    return TrackSearch.create(id).apply {
        remote_id = seriesId
        title = this@toTrackSearch.title?.htmlDecode() ?: ""
        total_chapters = this@toTrackSearch.latestChapter ?: 0
        cover_url = this@toTrackSearch.image?.url?.original ?: ""
        summary = this@toTrackSearch.description?.htmlDecode() ?: ""
        tracking_url = this@toTrackSearch.url ?: ""
        publishing_status = ""
        publishing_type = this@toTrackSearch.type ?: ""
        start_date = this@toTrackSearch.year ?: ""
    }
}
