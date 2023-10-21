@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.manga.impl.track.tracker.model

class TrackSearch {
    var sync_id: Int = 0

    var media_id: Long = 0

    lateinit var title: String

    var total_chapters: Int = 0

    lateinit var tracking_url: String

    var cover_url: String = ""

    var summary: String = ""

    var publishing_status: String = ""

    var publishing_type: String = ""

    var start_date: String = ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrackSearch

        if (sync_id != other.sync_id) return false
        if (media_id != other.media_id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sync_id.hashCode()
        result = 31 * result + media_id.hashCode()
        return result
    }

    companion object {
        fun create(serviceId: Int): TrackSearch =
            TrackSearch().apply {
                sync_id = serviceId
            }
    }
}
