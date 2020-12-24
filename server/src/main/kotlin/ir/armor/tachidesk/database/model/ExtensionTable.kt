package ir.armor.tachidesk.database.model

import eu.kanade.tachiyomi.source.Source
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table


object ExtensionsTable : IntIdTable() {
    val name = varchar("name", 128)
    val pkgName = varchar("pkg_name", 128)
    val versionName = varchar("version_name", 16)
    val versionCode = integer("version_code")
    val lang = varchar("lang", 5)
    val isNsfw  = bool("is_nsfw")
}

//class Extension(id: EntityID<Int>) : IntEntity(id) {
//    companion object : IntEntityClass<Extension>(ExtensionsTable)
//
//    val name by ExtensionsTable.name
//    val pkgName by ExtensionsTable.pkgName
//    val versionName by ExtensionsTable.versionName
//    val versionCode by ExtensionsTable.versionCode
//    val lang by ExtensionsTable.lang
//    val isNsfw by ExtensionsTable.isNsfw
//}
