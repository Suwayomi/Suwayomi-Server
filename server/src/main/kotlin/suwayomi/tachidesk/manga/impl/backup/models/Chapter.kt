@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.manga.impl.backup.models

import eu.kanade.tachiyomi.source.model.SChapter
import java.io.Serializable

interface Chapter : SChapter, Serializable {
    var id: Long?

    var manga_id: Long?

    var read: Boolean

    var bookmark: Boolean

    var last_page_read: Int

    var date_fetch: Long

    var source_order: Int

    val isRecognizedNumber: Boolean
        get() = chapter_number >= 0f
}
