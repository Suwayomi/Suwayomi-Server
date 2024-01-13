@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.manga.impl.backup.models

import java.io.Serializable

interface Track : Serializable {
    var id: Long?

    var manga_id: Long

    var sync_id: Int

    var media_id: Int

    var library_id: Long?

    var title: String

    var last_chapter_read: Int

    var total_chapters: Int

    var score: Float

    var status: Int

    var started_reading_date: Long

    var finished_reading_date: Long

    var tracking_url: String
}
