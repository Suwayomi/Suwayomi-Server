package ir.armor.tachidesk.util

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.extension.model.Extension
import ir.armor.tachidesk.database.dataclass.ExtensionDataClass
import ir.armor.tachidesk.database.table.ExtensionsTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private object Data {
    var lastExtensionCheck: Long = 0
}

private fun extensionDatabaseIsEmtpy(): Boolean {
    return transaction {
        return@transaction ExtensionsTable.selectAll().count() == 0L
    }
}

fun getExtensionList(offline: Boolean = false): List<ExtensionDataClass> {
    // update if 60 seconds has passed or requested offline and database is empty
    if (Data.lastExtensionCheck + 60 * 1000 < System.currentTimeMillis() || (offline && extensionDatabaseIsEmtpy())) {
        println("Getting extensions list from the internet")
        Data.lastExtensionCheck = System.currentTimeMillis()
        var foundExtensions: List<Extension.Available>
        runBlocking {
            val api = ExtensionGithubApi()
            foundExtensions = api.findExtensions()
            transaction {
                foundExtensions.forEach { foundExtension ->
                    val extensionRecord = ExtensionsTable.select { ExtensionsTable.name eq foundExtension.name }.firstOrNull()
                    if (extensionRecord != null) {
                        // update the record
                        ExtensionsTable.update({ ExtensionsTable.name eq foundExtension.name }) {
                            it[name] = foundExtension.name
                            it[pkgName] = foundExtension.pkgName
                            it[versionName] = foundExtension.versionName
                            it[versionCode] = foundExtension.versionCode
                            it[lang] = foundExtension.lang
                            it[isNsfw] = foundExtension.isNsfw
                            it[apkName] = foundExtension.apkName
                            it[iconUrl] = foundExtension.iconUrl
                        }
                    } else {
                        // insert new record
                        ExtensionsTable.insert {
                            it[name] = foundExtension.name
                            it[pkgName] = foundExtension.pkgName
                            it[versionName] = foundExtension.versionName
                            it[versionCode] = foundExtension.versionCode
                            it[lang] = foundExtension.lang
                            it[isNsfw] = foundExtension.isNsfw
                            it[apkName] = foundExtension.apkName
                            it[iconUrl] = foundExtension.iconUrl
                        }
                    }
                }
            }
        }
    }

    return transaction {
        return@transaction ExtensionsTable.selectAll().map {
            ExtensionDataClass(
                it[ExtensionsTable.name],
                it[ExtensionsTable.pkgName],
                it[ExtensionsTable.versionName],
                it[ExtensionsTable.versionCode],
                it[ExtensionsTable.lang],
                it[ExtensionsTable.isNsfw],
                it[ExtensionsTable.apkName],
                it[ExtensionsTable.iconUrl],
                it[ExtensionsTable.installed],
                it[ExtensionsTable.classFQName]
            )
        }
    }
}
