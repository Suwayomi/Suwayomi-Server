package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import mu.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.extension.Extension.getExtensionIconUrl
import suwayomi.tachidesk.manga.impl.extension.github.ExtensionGithubApi
import suwayomi.tachidesk.manga.impl.extension.github.OnlineExtension
import suwayomi.tachidesk.manga.model.dataclass.ExtensionDataClass
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

object ExtensionsList {
    private val logger = KotlinLogging.logger {}

    var lastUpdateCheck: Long = 0
    var updateMap = ConcurrentHashMap<String, OnlineExtension>()

    suspend fun fetchExtensions() {
        // update if 60 seconds has passed or requested offline and database is empty
        if (lastUpdateCheck + 60.seconds.inWholeMilliseconds < System.currentTimeMillis()) {
            logger.debug("Getting extensions list from the internet")
            lastUpdateCheck = System.currentTimeMillis()

            val foundExtensions = ExtensionGithubApi.findExtensions()
            updateExtensionDatabase(foundExtensions)
        } else {
            logger.debug("used cached extension list")
        }
    }

    suspend fun getExtensionList(): List<ExtensionDataClass> {
        fetchExtensions()
        return extensionTableAsDataClass()
    }

    fun extensionTableAsDataClass() = transaction {
        ExtensionTable.selectAll().filter { it[ExtensionTable.name] != LocalSource.EXTENSION_NAME }.map {
            ExtensionDataClass(
                it[ExtensionTable.apkName],
                getExtensionIconUrl(it[ExtensionTable.apkName]),
                it[ExtensionTable.name],
                it[ExtensionTable.pkgName],
                it[ExtensionTable.versionName],
                it[ExtensionTable.versionCode],
                it[ExtensionTable.lang],
                it[ExtensionTable.isNsfw],
                it[ExtensionTable.isInstalled],
                it[ExtensionTable.hasUpdate],
                it[ExtensionTable.isObsolete]
            )
        }
    }

    private fun updateExtensionDatabase(foundExtensions: List<OnlineExtension>) {
        transaction {
            foundExtensions.forEach { foundExtension ->
                val extensionRecord = ExtensionTable.select { ExtensionTable.pkgName eq foundExtension.pkgName }.firstOrNull()
                if (extensionRecord != null) {
                    if (extensionRecord[ExtensionTable.isInstalled]) {
                        when {
                            foundExtension.versionCode > extensionRecord[ExtensionTable.versionCode] -> {
                                // there is an update
                                ExtensionTable.update({ ExtensionTable.pkgName eq foundExtension.pkgName }) {
                                    it[hasUpdate] = true
                                }
                                updateMap.putIfAbsent(foundExtension.pkgName, foundExtension)
                            }
                            foundExtension.versionCode < extensionRecord[ExtensionTable.versionCode] -> {
                                // somehow the user installed an invalid version
                                ExtensionTable.update({ ExtensionTable.pkgName eq foundExtension.pkgName }) {
                                    it[isObsolete] = true
                                }
                            }
                        }
                    } else {
                        // extension is not installed, so we can overwrite the data without a care
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
                    // not in the repo, so these extensions are obsolete
                    if (extensionRecord[ExtensionTable.isInstalled]) {
                        // is installed so we should mark it as obsolete
                        ExtensionTable.update({ ExtensionTable.pkgName eq extensionRecord[ExtensionTable.pkgName] }) {
                            it[isObsolete] = true
                        }
                    } else {
                        // is not installed, so we can remove the record without a care
                        ExtensionTable.deleteWhere { ExtensionTable.pkgName eq extensionRecord[ExtensionTable.pkgName] }
                    }
                }
            }
        }
    }
}
