package ir.armor.tachidesk.database.entity

import ir.armor.tachidesk.database.table.SourcesTable
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID

class SourceEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : EntityClass<Long, SourceEntity>(SourcesTable, null)

    var sourceId by SourcesTable.id
    var name by SourcesTable.name
    var lang by SourcesTable.lang
    var extension by ExtensionEntity referencedOn SourcesTable.extension
    var partOfFactorySource by SourcesTable.partOfFactorySource
    var positionInFactorySource by SourcesTable.positionInFactorySource
}