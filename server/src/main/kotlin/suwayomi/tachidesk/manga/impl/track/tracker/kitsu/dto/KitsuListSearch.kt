package suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.Kitsu
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.KitsuApi
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.KitsuDateHelper
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch

private val logger = KotlinLogging.logger {}

@Serializable
data class KitsuListSearchResult(
    val data: List<KitsuListSearchItemData> = emptyList(),
    val included: List<KitsuListSearchItemIncluded> = emptyList(),
) {
    /**
     * The `include=manga` payload may be absent even though the library entry
     * exists; in that case the entry's personal fields are still read from
     * `data` and the media-level fields fall back to [localTrack].
     */
    fun firstToTrack(localTrack: Track): Track {
        val userData = data.first()
        val userDataAttrs = userData.attributes
        val includedManga = included.firstOrNull()

        return TrackSearch.create(TrackerManager.KITSU).apply {
            remote_id = includedManga?.id?.toLongOrNull() ?: localTrack.remote_id
            library_id = userData.id.toLongOrNull()
            title = includedManga?.attributes?.canonicalTitle?.ifBlank { null } ?: localTrack.title
            total_chapters = includedManga?.attributes?.chapterCount ?: localTrack.total_chapters
            cover_url = includedManga?.attributes?.posterImage?.original ?: ""
            summary = includedManga?.attributes?.synopsis ?: ""
            tracking_url = KitsuApi.mangaUrl(remote_id)
            publishing_status = includedManga?.attributes?.status ?: ""
            publishing_type = includedManga?.attributes?.mangaType ?: ""
            start_date = userDataAttrs.startedAt ?: ""
            started_reading_date = KitsuDateHelper.parse(userDataAttrs.startedAt)
            finished_reading_date = KitsuDateHelper.parse(userDataAttrs.finishedAt)
            status =
                when (userDataAttrs.status) {
                    "current" -> {
                        Kitsu.READING
                    }

                    "completed" -> {
                        Kitsu.COMPLETED
                    }

                    "on_hold" -> {
                        Kitsu.ON_HOLD
                    }

                    "dropped" -> {
                        Kitsu.DROPPED
                    }

                    "planned" -> {
                        Kitsu.PLAN_TO_READ
                    }

                    // an unknown/added remote status must not kill the sync —
                    // keep the local value
                    else -> {
                        logger.warn { "Kitsu: unknown library status '${userDataAttrs.status}', keeping local" }
                        localTrack.status
                    }
                }
            score = userDataAttrs.ratingTwenty?.let { it / 2.0 } ?: 0.0
            last_chapter_read = (userDataAttrs.progress ?: 0).toDouble()
            private = userDataAttrs.private ?: false
        }
    }
}

@Serializable
data class KitsuListSearchItemData(
    // JSON:API resource ids are strings
    val id: String,
    val attributes: KitsuListSearchItemDataAttributes = KitsuListSearchItemDataAttributes(),
)

@Serializable
data class KitsuListSearchItemDataAttributes(
    val status: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val ratingTwenty: Int? = null,
    val progress: Int? = null,
    val private: Boolean? = null,
)

@Serializable
data class KitsuListSearchItemIncluded(
    // JSON:API resource ids are strings
    val id: String,
    val attributes: KitsuListSearchItemIncludedAttributes = KitsuListSearchItemIncludedAttributes(),
)

@Serializable
data class KitsuListSearchItemIncludedAttributes(
    val canonicalTitle: String = "",
    val chapterCount: Int? = null,
    val mangaType: String? = null,
    val posterImage: KitsuSearchItemCover? = null,
    val synopsis: String? = null,
    val startDate: String? = null,
    val status: String? = null,
)
