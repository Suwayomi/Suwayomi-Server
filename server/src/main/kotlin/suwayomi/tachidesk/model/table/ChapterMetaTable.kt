package suwayomi.tachidesk.model.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object ChapterMetaTable : IntIdTable() {
    val key = varchar("key", 256)
    val value = varchar("value", 4096)
    val ref = reference("chapter_ref", ChapterTable, ReferenceOption.CASCADE)
}
