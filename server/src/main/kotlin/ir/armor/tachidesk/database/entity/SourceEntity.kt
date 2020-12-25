package ir.armor.tachidesk.database.entity

import ir.armor.tachidesk.database.table.SourcesTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class SourceEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SourceEntity>(SourcesTable)

    var sourceId by SourcesTable.sourceId
    var name by SourcesTable.name
    var lang by SourcesTable.lang
    var extension by ExtensionEntity referencedOn SourcesTable.extension
    var partOfFactorySource by SourcesTable.partOfFactorySource
    var positionInFactorySource by SourcesTable.positionInFactorySource
}