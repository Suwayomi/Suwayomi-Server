package ir.armor.tachidesk.database.table

import org.jetbrains.exposed.dao.id.IntIdTable


object ExtensionsTable : IntIdTable() {
    val name = varchar("name", 128)
    val pkgName = varchar("pkg_name", 128)
    val versionName = varchar("version_name", 16)
    val versionCode = integer("version_code")
    val lang = varchar("lang", 10)
    val isNsfw = bool("is_nsfw")
    val apkName = varchar("apk_name", 1024)
    val iconUrl = varchar("icon_url", 2048)

    val installed = bool("installed").default(false)
    val classFQName = varchar("class_name", 256).default("") // fully qualified name
}