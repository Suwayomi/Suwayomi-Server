package net.odorcave.kubinashi.model

import eu.kanade.tachiyomi.source.model.SChapter

data class Chapter(
    var url: String,
    var name: String,
    var date_upload: Long,
    var chapter_number: Double,
    var scanlator: String?,
    val source_order: Long,
) {
    val isRecognizedNumber: Boolean
        get() = chapter_number >= 0f

    fun copyFromSChapter(sChapter: SChapter): Chapter {
        return this.copy(
            url = sChapter.url,
            name = sChapter.name,
            date_upload = sChapter.date_upload,
            chapter_number = sChapter.chapter_number.toDouble(),
            scanlator = sChapter.scanlator?.ifBlank { null }?.trim(),
        )
    }

    fun toSChapter(): SChapter {
        return SChapter.create().also {
            it.url = url
            it.name = name
            it.date_upload = date_upload
            it.chapter_number = chapter_number.toFloat()
            it.scanlator = scanlator
        }
    }

    companion object {
        fun create() = Chapter(
            url = "",
            name = "",
            date_upload = -1,
            chapter_number = -1.0,
            scanlator = null,
            source_order = 0,
        )

    }
}
