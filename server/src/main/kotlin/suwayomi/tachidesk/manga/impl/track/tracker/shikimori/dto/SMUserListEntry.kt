package suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.ShikimoriApi
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.toTrackStatus

private val logger = KotlinLogging.logger {}

@Serializable
data class SMUserListEntry(
    val id: Long,
    val chapters: Double = 0.0,
    val score: Int = 0,
    val status: String? = null,
) {
    /**
     * [localStatus] is the status of the local track this entry is refreshed
     * for; an absent/unknown remote status keeps it instead of persisting the
     * unread sentinel 0, which wedged every later push on
     * `toShikimoriStatus(0)`'s IllegalArgumentException. The 0 default is for
     * standalone parses where no local track exists.
     */
    fun toTrack(
        trackId: Int,
        manga: SMManga,
        localStatus: Int = 0,
    ): Track =
        Track.create(trackId).apply {
            title = manga.name
            // remote_id is the MANGA id — this entry's own id is the
            // user_rate id, which only belongs in library_id
            remote_id = manga.id
            total_chapters = manga.chapters ?: 0
            library_id = this@SMUserListEntry.id
            last_chapter_read = this@SMUserListEntry.chapters
            score = this@SMUserListEntry.score.toDouble()
            status =
                toTrackStatus(this@SMUserListEntry.status)
                    ?: localStatus.also {
                        logger.warn {
                            "Shikimori: unmapped user_rate status '${this@SMUserListEntry.status}' on entry $id — keeping local status $localStatus"
                        }
                    }
            tracking_url = ShikimoriApi.BASE_URL + manga.url
        }
}
