package suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto

import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.KitsuApi
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class KitsuSearchResult(
    val media: KitsuSearchResultData,
)

@Serializable
data class KitsuSearchResultData(
    val key: String,
)

@Serializable
data class KitsuAlgoliaSearchResult(
    val hits: List<KitsuAlgoliaSearchItem>,
)

@Serializable
data class KitsuAlgoliaSearchItem(
    val id: Long,
    val canonicalTitle: String,
    val chapterCount: Int?,
    val subtype: String?,
    val posterImage: KitsuSearchItemCover?,
    val synopsis: String?,
    val averageRating: Double?,
    val startDate: Long?,
    val endDate: Long?,
) {
    fun toTrack(): TrackSearch =
        TrackSearch.create(TrackerManager.KITSU).apply {
            remote_id = this@KitsuAlgoliaSearchItem.id
            title = canonicalTitle
            total_chapters = chapterCount ?: 0
            cover_url = posterImage?.original ?: ""
            summary = synopsis ?: ""
            tracking_url = KitsuApi.mangaUrl(remote_id)
            score = averageRating ?: -1.0
            publishing_status = if (endDate == null) "Publishing" else "Finished"
            publishing_type = subtype ?: ""
            start_date = startDate?.let {
                val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                outputDf.format(Date(it * 1000))
            } ?: ""
        }
}
