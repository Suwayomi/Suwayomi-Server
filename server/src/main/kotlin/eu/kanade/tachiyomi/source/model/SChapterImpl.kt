@file:Suppress("ktlint:standard:property-naming")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY

class SChapterImpl : SChapter {
    override lateinit var url: String

    override lateinit var name: String

    override var chapter_number: Float = -1f

    override var scanlator: String? = null

    override var date_upload: Long = 0

    override var memo: JsonObject = JsonObject.EMPTY
}
