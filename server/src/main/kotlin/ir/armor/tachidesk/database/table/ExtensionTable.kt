package ir.armor.tachidesk.database.table

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable

object ExtensionTable : IntIdTable() {
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
