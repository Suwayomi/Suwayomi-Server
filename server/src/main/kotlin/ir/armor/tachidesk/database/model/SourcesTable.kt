package ir.armor.tachidesk.database.model

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object SourcesTable : IntIdTable() {
    val sourceId = long("source_id")
    val name= varchar("name", 128)
    val lang= varchar("lang", 5)
    val extension = reference("extension", ExtensionsTable)
    val partOfFactorySource = bool("part_of_factory_source").default(false)
    val positionInFactorySource = integer("position_in_factory_source").nullable()
}

class SourceEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SourceEntity>(SourcesTable)

    var sourceId by SourcesTable.sourceId
    var name by SourcesTable.name
    var lang by SourcesTable.lang
    var extension by ExtensionEntity referencedOn SourcesTable.extension
    var partOfFactorySource by SourcesTable.partOfFactorySource
    var positionInFactorySource by SourcesTable.positionInFactorySource
}


//object SourcesTable : IdTable<Long>() {
//    override val id = long("id").entityId()
//    val name= varchar("name", 128)
//    val extension = reference("extension", ExtensionsTable)
//    val partOfFactorySource = bool("part_of_factory_source").default(false)
//    val positionInFactorySource = integer("position_in_factory_source").nullable()
//
//    override val primaryKey = PrimaryKey(id)
//}
//
//class SourceEntity(id: EntityID<Long>) : LongEntity(id) {
//    companion object : LongEntityClass<SourceEntity>(SourcesTable)
//
//    var name by SourcesTable.name
//    var extension by SourcesTable.extension
//    var partOfFactorySource by SourcesTable.partOfFactorySource
//    var positionInFactorySource by SourcesTable.positionInFactorySource
//}
