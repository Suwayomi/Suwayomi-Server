@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.manga.impl.track.tracker.model

class TrackImpl : Track {
    override var id: Int? = null

    override var manga_id: Int = 0

    override var sync_id: Int = 0

    override var media_id: Long = 0

    override var library_id: Long? = null

    override lateinit var title: String

    override var last_chapter_read: Float = 0F

    override var total_chapters: Int = 0

    override var score: Float = 0f

    override var status: Int = 0

    override var started_reading_date: Long = 0

    override var finished_reading_date: Long = 0

    override var tracking_url: String = ""
}
