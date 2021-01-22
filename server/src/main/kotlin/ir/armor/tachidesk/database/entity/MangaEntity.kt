package ir.armor.tachidesk.database.entity

import ir.armor.tachidesk.database.table.MangaTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class MangaEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MangaEntity>(MangaTable)

    var url by MangaTable.url
    var title by MangaTable.title
    var initialized by MangaTable.initialized

    var artist by MangaTable.artist
    var author by MangaTable.author
    var description by MangaTable.description
    var genre by MangaTable.genre
    var status by MangaTable.status
    var thumbnail_url by MangaTable.thumbnail_url

    var sourceReference by MangaEntity referencedOn MangaTable.sourceReference
}
