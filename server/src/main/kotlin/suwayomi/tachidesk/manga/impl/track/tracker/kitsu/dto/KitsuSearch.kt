package suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto

import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.KitsuApi
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    val hits: List<KitsuAlgoliaSearchItem> = emptyList(),
)

@Serializable
data class KitsuAlgoliaSearchItem(
    val id: Long,
    val canonicalTitle: String = "",
    val chapterCount: Int? = null,
    val subtype: String? = null,
    val posterImage: KitsuSearchItemCover? = null,
    val synopsis: String? = null,
    val averageRating: Double? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
) {
    fun toTrack(): TrackSearch =
        TrackSearch.create(TrackerManager.KITSU).apply {
            remote_id = this@KitsuAlgoliaSearchItem.id
            title = canonicalTitle
            total_chapters = chapterCount ?: 0
            cover_url = posterImage?.original ?: ""
            summary = synopsis ?: ""
            tracking_url = KitsuApi.mangaUrl(remote_id)
            score = averageRating ?: 0.0
            publishing_status = if (endDate == null) "Publishing" else "Finished"
            publishing_type = subtype ?: ""
            start_date =
                startDate?.let {
                    // epoch seconds from Algolia — render in UTC so the
                    // JVM default zone cannot shift the date
                    val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    outputDf.timeZone = TimeZone.getTimeZone("UTC")
                    outputDf.format(Date(it * 1000))
                } ?: ""
        }
}

/**
 * The edge API (`GET /manga/{id}`, used by the `id:` search prefix) returns a
 * JSON:API document whose numeric fields differ from Algolia's — notably
 * `averageRating` is a percentage string there.
 */
@Serializable
data class KitsuMangaResponse(
    val data: KitsuMangaData? = null,
)

@Serializable
data class KitsuMangaData(
    val id: String,
    val attributes: KitsuMangaAttributes = KitsuMangaAttributes(),
) {
    fun toTrack(fallbackId: Long): TrackSearch =
        TrackSearch.create(TrackerManager.KITSU).apply {
            remote_id = this@KitsuMangaData.id.toLongOrNull() ?: fallbackId
            title = attributes.canonicalTitle
            total_chapters = attributes.chapterCount ?: 0
            cover_url = attributes.posterImage?.original ?: ""
            summary = attributes.synopsis ?: ""
            tracking_url = KitsuApi.mangaUrl(remote_id)
            score = attributes.averageRating?.toDoubleOrNull() ?: 0.0
            publishing_status = if (attributes.endDate == null) "Publishing" else "Finished"
            publishing_type = attributes.subtype ?: ""
            start_date = attributes.startDate ?: ""
        }
}

@Serializable
data class KitsuMangaAttributes(
    val canonicalTitle: String = "",
    val chapterCount: Int? = null,
    val subtype: String? = null,
    val posterImage: KitsuSearchItemCover? = null,
    val synopsis: String? = null,
    val averageRating: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
)
