package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.extension.model.Extension
import ir.armor.tachidesk.model.database.ExtensionTable
import ir.armor.tachidesk.model.dataclass.ExtensionDataClass
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

object ExtensionListData {
    var lastUpdateCheck: Long = 0
    var updateMap = ConcurrentHashMap<String, Extension.Available>()
}

// const val ExtensionUpdateDelayTime = 60 * 1000 // 60,000 milliseconds = 60 seconds
const val ExtensionUpdateDelayTime = 0

fun getExtensionList(): List<ExtensionDataClass> {
    // update if {ExtensionUpdateDelayTime} seconds has passed or requested offline and database is empty
    if (ExtensionListData.lastUpdateCheck + ExtensionUpdateDelayTime < System.currentTimeMillis()) {
        logger.debug("Getting extensions list from the internet")
        ExtensionListData.lastUpdateCheck = System.currentTimeMillis()
        runBlocking {
            val foundExtensions = ExtensionGithubApi.findExtensions()
            updateExtensionDatabase(foundExtensions)
        }
    } else {
        logger.debug("used cached extension list")
    }

    return extensionTableAsDataClass()
}

fun extensionTableAsDataClass() = transaction {
    ExtensionTable.selectAll().map {
        ExtensionDataClass(
            it[ExtensionTable.name],
            it[ExtensionTable.pkgName],
            it[ExtensionTable.versionName],
            it[ExtensionTable.versionCode],
            it[ExtensionTable.lang],
            it[ExtensionTable.isNsfw],
            it[ExtensionTable.apkName],
            getExtensionIconUrl(it[ExtensionTable.apkName]),
            it[ExtensionTable.isInstalled],
            it[ExtensionTable.hasUpdate],
            it[ExtensionTable.isObsolete],
        )
    }
}

private fun updateExtensionDatabase(foundExtensions: List<Extension.Available>) {
    transaction {
        foundExtensions.forEach { foundExtension ->
            val extensionRecord = ExtensionTable.select { ExtensionTable.pkgName eq foundExtension.pkgName }.firstOrNull()
            if (extensionRecord != null) {
                if (extensionRecord[ExtensionTable.isInstalled]) {
                    if (foundExtension.versionCode > extensionRecord[ExtensionTable.versionCode]) {
                        // there is an update
                        ExtensionTable.update({ ExtensionTable.pkgName eq foundExtension.pkgName }) {
                            it[hasUpdate] = true
                        }
                        ExtensionListData.updateMap.putIfAbsent(foundExtension.pkgName, foundExtension)
                    } else if (foundExtension.versionCode < extensionRecord[ExtensionTable.versionCode]) {
                        // some how the user installed an invalid version
                        ExtensionTable.update({ ExtensionTable.pkgName eq foundExtension.pkgName }) {
                            it[isObsolete] = true
                        }
                    } else {
                        // the two are equal
                        // NOOP
                    }
                } else {
                    // extension is not installed so we can overwrite the data without a care
                    ExtensionTable.update({ ExtensionTable.pkgName eq foundExtension.pkgName }) {
                        it[name] = foundExtension.name
                        it[versionName] = foundExtension.versionName
                        it[versionCode] = foundExtension.versionCode
                        it[lang] = foundExtension.lang
                        it[isNsfw] = foundExtension.isNsfw
                        it[apkName] = foundExtension.apkName
                        it[iconUrl] = foundExtension.iconUrl
                    }
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

        // deal with obsolete extensions
        ExtensionTable.selectAll().forEach { extensionRecord ->
            val foundExtension = foundExtensions.find { it.pkgName == extensionRecord[ExtensionTable.pkgName] }
            if (foundExtension == null) {
                // this extensions is obsolete
                if (extensionRecord[ExtensionTable.isInstalled]) {
                    // is installed so we should mark it as obsolete
                    ExtensionTable.update({ ExtensionTable.pkgName eq extensionRecord[ExtensionTable.pkgName] }) {
                        it[isObsolete] = true
                    }
                } else {
                    // is not installed so we can remove the record without a care
                    ExtensionTable.deleteWhere { ExtensionTable.pkgName eq extensionRecord[ExtensionTable.pkgName] }
                }
            }
        }
    }
}
