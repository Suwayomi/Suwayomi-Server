package ir.armor.tachidesk.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object MangaTable : IntIdTable() {
    val url = varchar("url", 2048)
    val title = varchar("title", 512)
    val initialized = bool("initialized").default(false)

    val artist = varchar("artist", 64).nullable()
    val author = varchar("author", 64).nullable()
    val description = varchar("description", 4096).nullable()
    val genre = varchar("genre", 1024).nullable()
    val status = enumeration("status", MangaStatus::class).default(MangaStatus.UNKNOWN)
    val thumbnail_url = varchar("thumbnail_url", 2048).nullable()

    // source is used by some ancestor of IntIdTable
    val sourceReference = reference("source", SourceTable)
}

enum class MangaStatus(val status: Int) {
    UNKNOWN(0),
    ONGOING(1),
    COMPLETED(2),
    LICENSED(3),
}