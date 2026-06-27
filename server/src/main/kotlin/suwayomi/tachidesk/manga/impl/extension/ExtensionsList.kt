package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.extension.Extension.proxyExtensionIconUrl
import suwayomi.tachidesk.manga.model.dataclass.ContentWarning
import suwayomi.tachidesk.manga.model.dataclass.ExtensionDataClass
import suwayomi.tachidesk.manga.model.dataclass.ExtensionInfo
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

object ExtensionsList {
    private val logger = KotlinLogging.logger {}

    var lastUpdateCheck: Long = 0
    var updateMap = ConcurrentHashMap<String, ExtensionInfo>()

    suspend fun fetchExtensions() {
        val allExtensions = mutableListOf<ExtensionInfo>()

        ExtensionStoreService.getAndRefresh().forEach { store ->
            try {
                val extensions = ExtensionStoreService.getExtensions(store)
                allExtensions.addAll(extensions)
            } catch (e: Exception) {
                logger.warn(e) {
                    "Failed to fetch extensions for store: ${store.indexUrl}"
                }
            }
        }

        updateExtensionDatabase(allExtensions)
    }

    suspend fun fetchExtensionsCached() {
        // update if 60 seconds has passed or requested offline and database is empty
        if (lastUpdateCheck + 60.seconds.inWholeMilliseconds < System.currentTimeMillis()) {
            logger.debug { "Getting extensions list from the internet" }
            lastUpdateCheck = System.currentTimeMillis()

            fetchExtensions()
        } else {
            logger.debug { "used cached extension list" }
        }
    }

    suspend fun getExtensionList(): List<ExtensionDataClass> {
        fetchExtensionsCached()
        return extensionTableAsDataClass()
    }

    fun extensionTableAsDataClass() =
        transaction {
            ExtensionTable.selectAll().filter { it[ExtensionTable.name] != LocalSource.EXTENSION_NAME }.map {
                ExtensionDataClass(
                    repo = it[ExtensionTable.storeIndexUrl],
                    apkName = it[ExtensionTable.apkName].orEmpty(),
                    iconUrl = proxyExtensionIconUrl(it[ExtensionTable.pkgName]),
                    name = it[ExtensionTable.name],
                    pkgName = it[ExtensionTable.pkgName],
                    versionName = it[ExtensionTable.versionName],
                    versionCode = it[ExtensionTable.versionCode].toInt(),
                    lang = it[ExtensionTable.lang],
                    isNsfw = it[ExtensionTable.contentWarning] >= ContentWarning.MIXED.ordinal,
                    installed = it[ExtensionTable.isInstalled],
                    hasUpdate = it[ExtensionTable.hasUpdate],
                    obsolete = it[ExtensionTable.isObsolete],
                )
            }
        }

    private val updateExtensionDatabaseMutex = Mutex()

    private suspend fun updateExtensionDatabase(foundExtensions: List<ExtensionInfo>) {
        updateExtensionDatabaseMutex.withLock {
            transaction {
                val uniqueExtensions =
                    foundExtensions
                        .groupBy { it.pkgName }
                        .mapValues { (_, extension) ->
                            extension.maxBy { it.versionCode }
                        }.values
                val installedExtensions =
                    ExtensionTable
                        .selectAll()
                        .toList()
                        .associateBy { it[ExtensionTable.pkgName] }
                val extensionsToUpdate = mutableListOf<Pair<ExtensionInfo, ResultRow>>()
                val extensionsToInsert = mutableListOf<ExtensionInfo>()
                val extensionsToDelete =
                    installedExtensions.filter { it.value[ExtensionTable.storeIndexUrl] != null }.mapNotNull { (pkgName, extension) ->
                        extension.takeUnless { uniqueExtensions.any { it.pkgName == pkgName } }
                    }
                uniqueExtensions.forEach {
                    val extension = installedExtensions[it.pkgName]
                    if (extension != null) {
                        extensionsToUpdate.add(it to extension)
                    } else {
                        extensionsToInsert.add(it)
                    }
                }
                if (extensionsToUpdate.isNotEmpty()) {
                    val extensionsInstalled =
                        extensionsToUpdate
                            .groupBy { it.second[ExtensionTable.isInstalled] }
                    val installedExtensionsToUpdate = extensionsInstalled[true].orEmpty()
                    if (installedExtensionsToUpdate.isNotEmpty()) {
                        BatchUpdateStatement(ExtensionTable)
                            .apply {
                                installedExtensionsToUpdate.forEach { (foundExtension, extensionRecord) ->
                                    addBatch(EntityID(extensionRecord[ExtensionTable.id].value, ExtensionTable))
                                    // Always update icon url and repo
                                    this[ExtensionTable.iconUrl] = foundExtension.iconUrl
                                    this[ExtensionTable.storeIndexUrl] = foundExtension.storeIndexUrl

                                    // add these because batch updates need matching columns
                                    this[ExtensionTable.hasUpdate] = extensionRecord[ExtensionTable.hasUpdate]
                                    this[ExtensionTable.isObsolete] = extensionRecord[ExtensionTable.isObsolete]

                                    // a previously removed extension is now available again
                                    if (extensionRecord[ExtensionTable.isObsolete] &&
                                        foundExtension.versionCode >= extensionRecord[ExtensionTable.versionCode]
                                    ) {
                                        this[ExtensionTable.isObsolete] = false
                                    }

                                    when {
                                        foundExtension.versionCode > extensionRecord[ExtensionTable.versionCode] -> {
                                            // there is an update
                                            this[ExtensionTable.hasUpdate] = true
                                            updateMap.putIfAbsent(foundExtension.pkgName, foundExtension)
                                        }

                                        foundExtension.versionCode < extensionRecord[ExtensionTable.versionCode] -> {
                                            // somehow the user installed an invalid version
                                            this[ExtensionTable.isObsolete] = true
                                        }
                                    }
                                }
                            }.toExecutable()
                            .execute(this@transaction)
                    }
                    val extensionsToFullyUpdate = extensionsInstalled[false].orEmpty()
                    if (extensionsToFullyUpdate.isNotEmpty()) {
                        BatchUpdateStatement(ExtensionTable)
                            .apply {
                                extensionsToFullyUpdate.forEach { (foundExtension, extensionRecord) ->
                                    addBatch(EntityID(extensionRecord[ExtensionTable.id].value, ExtensionTable))
                                    // extension is not installed, so we can overwrite the data without a care
                                    this[ExtensionTable.storeIndexUrl] = foundExtension.storeIndexUrl
                                    this[ExtensionTable.name] = foundExtension.name
                                    this[ExtensionTable.extensionLib] = foundExtension.extensionLib
                                    this[ExtensionTable.versionName] = foundExtension.versionName
                                    this[ExtensionTable.versionCode] = foundExtension.versionCode
                                    this[ExtensionTable.lang] = foundExtension.lang
                                    this[ExtensionTable.contentWarning] = foundExtension.contentWarning.ordinal
                                    this[ExtensionTable.apkUrl] = foundExtension.apkUrl
                                    this[ExtensionTable.iconUrl] = foundExtension.iconUrl
                                }
                            }.toExecutable()
                            .execute(this@transaction)
                    }
                }
                if (extensionsToInsert.isNotEmpty()) {
                    ExtensionTable.batchInsert(extensionsToInsert) { foundExtension ->
                        this[ExtensionTable.storeIndexUrl] = foundExtension.storeIndexUrl
                        this[ExtensionTable.name] = foundExtension.name
                        this[ExtensionTable.pkgName] = foundExtension.pkgName
                        this[ExtensionTable.extensionLib] = foundExtension.extensionLib
                        this[ExtensionTable.versionName] = foundExtension.versionName
                        this[ExtensionTable.versionCode] = foundExtension.versionCode
                        this[ExtensionTable.lang] = foundExtension.lang
                        this[ExtensionTable.contentWarning] = foundExtension.contentWarning.ordinal
                        this[ExtensionTable.apkUrl] = foundExtension.apkUrl
                        this[ExtensionTable.iconUrl] = foundExtension.iconUrl
                    }
                }

                // deal with obsolete extensions
                val extensionsToRemove =
                    extensionsToDelete
                        .groupBy { it[ExtensionTable.isInstalled] }
                        .mapValues { (_, extensions) -> extensions.map { it[ExtensionTable.pkgName] } }
                // not in the repo, so these extensions are obsolete
                val obsoleteExtensions = extensionsToRemove[true].orEmpty()
                if (obsoleteExtensions.isNotEmpty()) {
                    ExtensionTable.update({ ExtensionTable.pkgName inList obsoleteExtensions }) {
                        it[isObsolete] = true
                    }
                }
                // is not installed, so we can remove the record without a care
                val removeExtensions = extensionsToRemove[false].orEmpty()
                if (removeExtensions.isNotEmpty()) {
                    ExtensionTable.deleteWhere { ExtensionTable.pkgName inList removeExtensions }
                }
            }
        }
    }
}
