package ir.armor.tachidesk.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object SourcesTable : IntIdTable() {
    val sourceId = long("source_id")
    val name = varchar("name", 128)
    val lang = varchar("lang", 5)
    val extension = reference("extension", ExtensionsTable)
    val partOfFactorySource = bool("part_of_factory_source").default(false)
    val positionInFactorySource = integer("position_in_factory_source").nullable()
}