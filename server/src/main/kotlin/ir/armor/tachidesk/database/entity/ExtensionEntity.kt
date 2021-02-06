package ir.armor.tachidesk.database.entity

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.database.table.ExtensionTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ExtensionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ExtensionEntity>(ExtensionTable)

    var name by ExtensionTable.name
    var pkgName by ExtensionTable.pkgName
    var versionName by ExtensionTable.versionName
    var versionCode by ExtensionTable.versionCode
    var lang by ExtensionTable.lang
    var isNsfw by ExtensionTable.isNsfw
    var apkName by ExtensionTable.apkName
    var iconUrl by ExtensionTable.iconUrl
    var installed by ExtensionTable.installed
    var classFQName by ExtensionTable.classFQName
}
