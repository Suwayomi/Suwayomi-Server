package ir.armor.tachidesk.database.model

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object SourcesTable : Table() {
    val id: Column<Long> = long("id")
    val name: Column<String> = varchar("name", 128)
    val extension = reference("extension", ExtensionsTable)
    override val primaryKey = PrimaryKey(id)
}

//class Source : Entity() {
//    companion object : Entity<Source>(SourcesTable)
//
//    val name by ExtensionsTable.name
//    val pkgName by ExtensionsTable.pkgName
//    val versionName by ExtensionsTable.versionName
//    val versionCode by ExtensionsTable.versionCode
//    val lang by ExtensionsTable.lang
//    val isNsfw by ExtensionsTable.isNsfw
//}