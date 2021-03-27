package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.extension.model.Extension
import ir.armor.tachidesk.database.dataclass.ExtensionDataClass
import ir.armor.tachidesk.database.table.ExtensionTable
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val logger = KotlinLogging.logger {}

private object Data {
    var lastExtensionCheck: Long = 0
}

private fun extensionDatabaseIsEmtpy(): Boolean {
    return transaction {
        return@transaction ExtensionTable.selectAll().count() == 0L
    }
}

fun getExtensionList(offline: Boolean = false): List<ExtensionDataClass> {
    // update if 60 seconds has passed or requested offline and database is empty
    if (Data.lastExtensionCheck + 60 * 1000 < System.currentTimeMillis() || (offline && extensionDatabaseIsEmtpy())) {
        logger.info("Getting extensions list from the internet")
        Data.lastExtensionCheck = System.currentTimeMillis()
        var foundExtensions: List<Extension.Available>
        runBlocking {
            val api = ExtensionGithubApi()
            foundExtensions = api.findExtensions()
            transaction {
                foundExtensions.forEach { foundExtension ->
                    val extensionRecord = ExtensionTable.select { ExtensionTable.name eq foundExtension.name }.firstOrNull()
                    if (extensionRecord != null) {
                        // update the record
                        ExtensionTable.update({ ExtensionTable.name eq foundExtension.name }) {
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
                        ExtensionTable.insert {
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
    } else {
        logger.info("used cached extension list")
    }

    return transaction {
        return@transaction ExtensionTable.selectAll().map {
            ExtensionDataClass(
                it[ExtensionTable.name],
                it[ExtensionTable.pkgName],
                it[ExtensionTable.versionName],
                it[ExtensionTable.versionCode],
                it[ExtensionTable.lang],
                it[ExtensionTable.isNsfw],
                it[ExtensionTable.apkName],
                getExtensionIconUrl(it[ExtensionTable.apkName]),
                it[ExtensionTable.installed],
                it[ExtensionTable.classFQName]
            )
        }
    }
}
