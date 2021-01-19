package ir.armor.tachidesk.database.entity

import ir.armor.tachidesk.database.table.SourceTable
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID

class SourceEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : EntityClass<Long, SourceEntity>(SourceTable, null)

    var sourceId by SourceTable.id
    var name by SourceTable.name
    var lang by SourceTable.lang
    var extension by ExtensionEntity referencedOn SourceTable.extension
    var partOfFactorySource by SourceTable.partOfFactorySource
    var positionInFactorySource by SourceTable.positionInFactorySource
}