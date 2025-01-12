@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.manga.impl.track.tracker.model

import java.io.Serializable

interface Track : Serializable {
    var id: Int?

    var manga_id: Int

    var sync_id: Int

    var media_id: Long

    var library_id: Long?

    var title: String

    var last_chapter_read: Float

    var total_chapters: Int

    var score: Float

    var status: Int

    var started_reading_date: Long

    var finished_reading_date: Long

    var tracking_url: String

    fun copyPersonalFrom(other: Track) {
        last_chapter_read = other.last_chapter_read
        score = other.score
        status = other.status
        started_reading_date = other.started_reading_date
        finished_reading_date = other.finished_reading_date
    }

    companion object {
        fun create(serviceId: Int): Track =
            TrackImpl().apply {
                sync_id = serviceId
            }
    }
}
