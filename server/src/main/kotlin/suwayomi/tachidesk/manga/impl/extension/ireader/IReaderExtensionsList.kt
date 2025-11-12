package suwayomi.tachidesk.manga.impl.extension.ireader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.model.dataclass.IReaderExtensionDataClass
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable

object IReaderExtensionsList {
    private val logger = KotlinLogging.logger {}

    val updateMap = mutableMapOf<String, IReaderExtensionDataClass>()

    private const val IREADER_REPO_URL = "https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repo/index.json"

    fun extensionTableAsDataClass(): List<IReaderExtensionDataClass> =
        transaction {
            IReaderExtensionTable.selectAll().map {
                IReaderExtensionDataClass(
                    repo = it[IReaderExtensionTable.repo],
                    apkName = it[IReaderExtensionTable.apkName],
                    iconUrl = it[IReaderExtensionTable.iconUrl],
                    name = it[IReaderExtensionTable.name],
                    pkgName = it[IReaderExtensionTable.pkgName],
                    versionName = it[IReaderExtensionTable.versionName],
                    versionCode = it[IReaderExtensionTable.versionCode],
                    lang = it[IReaderExtensionTable.lang],
                    isNsfw = it[IReaderExtensionTable.isNsfw],
                    installed = it[IReaderExtensionTable.isInstalled],
                    hasUpdate = it[IReaderExtensionTable.hasUpdate],
                    obsolete = it[IReaderExtensionTable.isObsolete],
                )
            }
        }

    suspend fun getExtensionList(): List<IReaderExtensionDataClass> {
        // Fetch from GitHub and update database
        try {
            val onlineExtensions = IReaderGithubApi.findExtensions(IREADER_REPO_URL)
            updateExtensionDatabase(onlineExtensions)
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch IReader extensions from repository" }
        }

        return extensionTableAsDataClass()
    }

    private fun updateExtensionDatabase(onlineExtensions: List<OnlineIReaderExtension>) {
        transaction {
            val installedExtensions =
                IReaderExtensionTable
                    .selectAll()
                    .toList()
                    .associateBy { it[IReaderExtensionTable.pkgName] }

            val onlinePkgs = onlineExtensions.map { it.pkgName }.toSet()

            // Remove uninstalled extensions that are no longer in the repo
            val extensionsToRemove =
                installedExtensions
                    .filter { !it.value[IReaderExtensionTable.isInstalled] && !onlinePkgs.contains(it.key) }
                    .map { it.key }

            if (extensionsToRemove.isNotEmpty()) {
                IReaderExtensionTable.deleteWhere {
                    pkgName inList extensionsToRemove
                }
            }

            // Update or insert extensions
            onlineExtensions.forEach { ext ->
                val existing = installedExtensions[ext.pkgName]

                if (existing != null) {
                    val isInstalled = existing[IReaderExtensionTable.isInstalled]
                    val installedVersion = existing[IReaderExtensionTable.versionCode]
                    val hasUpdate = isInstalled && ext.versionCode > installedVersion

                    // Populate updateMap for extensions with updates
                    if (hasUpdate) {
                        logger.info { "Update available for ${ext.pkgName}: v$installedVersion -> v${ext.versionCode}" }
                        updateMap[ext.pkgName] =
                            IReaderExtensionDataClass(
                                repo = ext.repo,
                                apkName = ext.apkName,
                                iconUrl = ext.iconUrl,
                                name = ext.name,
                                pkgName = ext.pkgName,
                                versionName = ext.versionName,
                                versionCode = ext.versionCode,
                                lang = ext.lang,
                                isNsfw = ext.isNsfw,
                                installed = true,
                                hasUpdate = true,
                                obsolete = false,
                            )
                    }

                    IReaderExtensionTable.update({ IReaderExtensionTable.pkgName eq ext.pkgName }) {
                        it[repo] = ext.repo
                        it[apkName] = ext.apkName
                        it[iconUrl] = ext.iconUrl
                        it[name] = ext.name
                        it[versionName] = ext.versionName
                        it[versionCode] = ext.versionCode
                        it[lang] = ext.lang
                        it[isNsfw] = ext.isNsfw
                        it[IReaderExtensionTable.hasUpdate] = hasUpdate
                    }
                } else {
                    IReaderExtensionTable.insert {
                        it[repo] = ext.repo
                        it[apkName] = ext.apkName
                        it[iconUrl] = ext.iconUrl
                        it[name] = ext.name
                        it[pkgName] = ext.pkgName
                        it[versionName] = ext.versionName
                        it[versionCode] = ext.versionCode
                        it[lang] = ext.lang
                        it[isNsfw] = ext.isNsfw
                        it[isInstalled] = false
                        it[hasUpdate] = false
                        it[isObsolete] = false
                    }
                }
            }
        }
    }
}
