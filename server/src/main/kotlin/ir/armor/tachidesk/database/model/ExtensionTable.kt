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
    val isNsfw = bool("is_nsfw")
    val apkName = varchar("apk_name", 1024)
    val iconUrl = varchar("icon_url", 2048)

    val installed = bool("installed").default(false)
    val classFQName = varchar("class_name", 256).default("") // fully qualified name
}

data class ExtensionDataClass(
        val name: String,
        val pkgName: String,
        val versionName: String,
        val versionCode: Int,
        val lang: String,
        val isNsfw: Boolean,
        val apkName: String,
        val iconUrl : String,
        val installed: Boolean,
        val classFQName: String,
)
