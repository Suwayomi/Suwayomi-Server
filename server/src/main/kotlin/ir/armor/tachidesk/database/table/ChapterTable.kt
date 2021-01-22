package ir.armor.tachidesk.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object ChapterTable : IntIdTable() {
    val url = varchar("url", 2048)
    val name = varchar("name", 512)
    val date_upload = long("date_upload").default(0)
    val chapter_number = float("chapter_number").default(-1f)
    val scanlator = varchar("scanlator", 128).nullable()

    val manga = reference("manga", MangaTable)
}
