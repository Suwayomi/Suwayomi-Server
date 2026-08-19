package suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto

import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.ShikimoriApi
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.toTrackStatus

@Serializable
data class SMUserListEntry(
    val id: Long,
    val chapters: Double,
    val score: Int,
    val status: String,
) {
    fun toTrack(
        trackId: Int,
        manga: SMManga,
    ): Track =
        Track.create(trackId).apply {
            title = manga.name
            remote_id = this@SMUserListEntry.id
            total_chapters = manga.chapters
            library_id = this@SMUserListEntry.id
            last_chapter_read = this@SMUserListEntry.chapters
            score = this@SMUserListEntry.score.toDouble()
            status = toTrackStatus(this@SMUserListEntry.status)
            tracking_url = ShikimoriApi.BASE_URL + manga.url
        }
}
