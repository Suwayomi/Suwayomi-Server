package ir.armor.tachidesk.impl.backup.models

import ir.armor.tachidesk.model.database.table.ChapterTable
import org.jetbrains.exposed.sql.ResultRow

class ChapterImpl : Chapter {

    override var id: Long? = null

    override var manga_id: Long? = null

    override lateinit var url: String

    override lateinit var name: String

    override var scanlator: String? = null

    override var read: Boolean = false

    override var bookmark: Boolean = false

    override var last_page_read: Int = 0

    override var date_fetch: Long = 0

    override var date_upload: Long = 0

    override var chapter_number: Float = 0f

    override var source_order: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val chapter = other as Chapter
        if (url != chapter.url) return false
        return id == chapter.id
    }

    override fun hashCode(): Int {
        return url.hashCode() + id.hashCode()
    }

    // Tachidesk -->
    companion object {
        fun fromQuery(chapterRecord: ResultRow): ChapterImpl {
            return ChapterImpl().apply {
                url = chapterRecord[ChapterTable.url]
                read = chapterRecord[ChapterTable.isRead]
                bookmark = chapterRecord[ChapterTable.isBookmarked]
                last_page_read = chapterRecord[ChapterTable.lastPageRead]
            }
        }
    }
    // Tachidesk <--
}
