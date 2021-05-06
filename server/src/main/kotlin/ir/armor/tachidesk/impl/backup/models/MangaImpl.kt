package ir.armor.tachidesk.impl.backup.models

import ir.armor.tachidesk.model.database.table.MangaTable
import org.jetbrains.exposed.sql.ResultRow

open class MangaImpl : Manga {

    override var id: Long? = 0

    override var source: Long = -1

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var favorite: Boolean = false

    override var last_update: Long = 0

    override var date_added: Long = 0

    override var initialized: Boolean = false

    /** Reader mode value
     * ref: https://github.com/tachiyomiorg/tachiyomi/blob/ff369010074b058bb734ce24c66508300e6e9ac6/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingModeType.kt#L8
     * 0 -> Default
     * 1 -> Left to Right
     * 2 -> Right to Left
     * 3 -> Vertical
     * 4 -> Webtoon
     * 5 -> Continues Vertical
     */
    override var viewer: Int = 0

    /** Contains some useful info about
     */
    override var chapter_flags: Int = 0

    override var cover_last_modified: Long = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val manga = other as Manga
        if (url != manga.url) return false
        return id == manga.id
    }

    override fun hashCode(): Int {
        return url.hashCode() + id.hashCode()
    }

    // Tachidesk -->
    companion object {
        fun fromQuery(mangaRecord: ResultRow): MangaImpl {
            return MangaImpl().apply {
                url = mangaRecord[MangaTable.url]
                title = mangaRecord[MangaTable.title]
                source = mangaRecord[MangaTable.sourceReference]
                viewer = 0 // TODO: implement
                chapter_flags = 0 // TODO: implement
            }
        }
    }
    // Tachidesk <--
}
