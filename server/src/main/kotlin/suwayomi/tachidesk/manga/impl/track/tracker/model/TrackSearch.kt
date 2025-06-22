@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.manga.impl.track.tracker.model

class TrackSearch : Track {
    override var id: Int? = null

    override var manga_id: Int = 0

    override var tracker_id: Int = 0

    override var remote_id: Long = 0

    override var library_id: Long? = null

    override lateinit var title: String

    override var last_chapter_read: Double = 0.0

    override var total_chapters: Int = 0

    override var score: Double = 0.0

    override var status: Int = 0

    override var started_reading_date: Long = 0

    override var finished_reading_date: Long = 0

    override var private: Boolean = false

    override lateinit var tracking_url: String

    var authors: List<String> = emptyList()

    var artists: List<String> = emptyList()

    var cover_url: String = ""

    var summary: String = ""

    var publishing_status: String = ""

    var publishing_type: String = ""

    var start_date: String = ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrackSearch

        if (manga_id != other.manga_id) return false
        if (tracker_id != other.tracker_id) return false
        if (remote_id != other.remote_id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = manga_id.hashCode()
        result = 31 * result + tracker_id.hashCode()
        result = 31 * result + remote_id.hashCode()
        return result
    }

    companion object {
        fun create(serviceId: Int): TrackSearch =
            TrackSearch().apply {
                tracker_id = serviceId
            }
    }
}
