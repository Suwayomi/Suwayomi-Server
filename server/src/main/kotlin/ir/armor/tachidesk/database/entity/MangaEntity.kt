package ir.armor.tachidesk.database.entity

import ir.armor.tachidesk.database.table.MangasTable
import ir.armor.tachidesk.database.table.SourcesTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class MangaEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MangaEntity>(MangasTable)

    var url by MangasTable.url
    var title by MangasTable.title
    var initialized by MangasTable.initialized

    var artist by MangasTable.artist
    var author by MangasTable.author
    var description by MangasTable.description
    var genre by MangasTable.genre
    var status by MangasTable.status
    var thumbnail_url by MangasTable.thumbnail_url

    var sourceReference by MangaEntity referencedOn MangasTable.sourceReference
}