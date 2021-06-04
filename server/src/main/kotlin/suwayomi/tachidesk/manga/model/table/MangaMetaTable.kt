package suwayomi.tachidesk.manga.model.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object MangaMetaTable : IntIdTable() {
    val key = varchar("key", 256)
    val value = varchar("value", 4096)
    val ref = reference("manga_ref", MangaTable, ReferenceOption.CASCADE)
}
