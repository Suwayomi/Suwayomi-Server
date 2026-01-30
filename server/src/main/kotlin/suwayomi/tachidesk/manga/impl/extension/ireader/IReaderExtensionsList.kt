package suwayomi.tachidesk.manga.impl.extension.ireader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtension.getExtensionIconUrl
import suwayomi.tachidesk.manga.model.dataclass.IReaderExtensionDataClass
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the list of IReader extensions, including fetching from repositories
 * and updating the local database.
 * 
 * This implementation follows the same patterns as ExtensionsList for Tachiyomi extensions.
 */
object IReaderExtensionsList {
    private val logger = KotlinLogging.logger {}

    var lastUpdateCheck: Long = 0
    val updateMap = ConcurrentHashMap<String, IReaderExtensionDataClass>()

    private const val IREADER_REPO_URL = "https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2/index.json"

    /**
     * Fetch extensions from the repository and update the database.
     */
    suspend fun fetchExtensions() {
        val extensions = kotlin.runCatching {
            IReaderGithubApi.findExtensions(IREADER_REPO_URL)
        }.onFailure {
            logger.warn(it) { "Failed to fetch IReader extensions from repo: $IREADER_REPO_URL" }
        }

        extensions.getOrNull()?.let { foundExtensions ->
            updateExtensionDatabase(foundExtensions)
        }
    }

    /**
     * Fetch extensions with caching (60 second cache).
     */
    suspend fun fetchExtensionsCached() {
        if (lastUpdateCheck + 60.seconds.inWholeMilliseconds < System.currentTimeMillis()) {
            logger.debug { "Getting IReader extensions list from the internet" }
            lastUpdateCheck = System.currentTimeMillis()
            fetchExtensions()
        } else {
            logger.debug { "Used cached IReader extension list" }
        }
    }

    /**
     * Get the list of IReader extensions.
     */
    suspend fun getExtensionList(): List<IReaderExtensionDataClass> {
        fetchExtensionsCached()
        return extensionTableAsDataClass()
    }

    /**
     * Convert extension table to data class list.
     */
    fun extensionTableAsDataClass(): List<IReaderExtensionDataClass> =
        transaction {
            IReaderExtensionTable.selectAll().map {
                IReaderExtensionDataClass(
                    repo = it[IReaderExtensionTable.repo],
                    apkName = it[IReaderExtensionTable.apkName],
                    iconUrl = getExtensionIconUrl(it[IReaderExtensionTable.apkName]),
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

    private val updateExtensionDatabaseMutex = Mutex()

    /**
     * Update the extension database with online extensions.
     * Uses batch operations for efficiency, following the same pattern as ExtensionsList.
     */
    private suspend fun updateExtensionDatabase(foundExtensions: List<OnlineIReaderExtension>) {
        updateExtensionDatabaseMutex.withLock {
            transaction {
                // Get unique extensions (highest version code wins)
                val uniqueExtensions = foundExtensions
                    .groupBy { it.pkgName }
                    .mapValues { (_, extensions) -> extensions.maxBy { it.versionCode } }
                    .values

                val installedExtensions = IReaderExtensionTable
                    .selectAll()
                    .toList()
                    .associateBy { it[IReaderExtensionTable.pkgName] }

                val extensionsToUpdate = mutableListOf<Pair<OnlineIReaderExtension, ResultRow>>()
                val extensionsToInsert = mutableListOf<OnlineIReaderExtension>()
                val extensionsToDelete = installedExtensions
                    .filter { it.value[IReaderExtensionTable.repo] != null }
                    .mapNotNull { (pkgName, extension) ->
                        extension.takeUnless { uniqueExtensions.any { it.pkgName == pkgName } }
                    }

                uniqueExtensions.forEach { ext ->
                    val extension = installedExtensions[ext.pkgName]
                    if (extension != null) {
                        extensionsToUpdate.add(ext to extension)
                    } else {
                        extensionsToInsert.add(ext)
                    }
                }

                // Process updates
                if (extensionsToUpdate.isNotEmpty()) {
                    val extensionsInstalled = extensionsToUpdate
                        .groupBy { it.second[IReaderExtensionTable.isInstalled] }

                    // Update installed extensions (check for updates)
                    val installedExtensionsToUpdate = extensionsInstalled[true].orEmpty()
                    if (installedExtensionsToUpdate.isNotEmpty()) {
                        BatchUpdateStatement(IReaderExtensionTable).apply {
                            installedExtensionsToUpdate.forEach { (foundExtension, extensionRecord) ->
                                addBatch(EntityID(extensionRecord[IReaderExtensionTable.id].value, IReaderExtensionTable))

                                // Always update icon url and repo
                                this[IReaderExtensionTable.iconUrl] = foundExtension.iconUrl
                                this[IReaderExtensionTable.repo] = foundExtension.repo

                                // Add these because batch updates need matching columns
                                this[IReaderExtensionTable.hasUpdate] = extensionRecord[IReaderExtensionTable.hasUpdate]
                                this[IReaderExtensionTable.isObsolete] = extensionRecord[IReaderExtensionTable.isObsolete]

                                // A previously removed extension is now available again
                                if (extensionRecord[IReaderExtensionTable.isObsolete] &&
                                    foundExtension.versionCode >= extensionRecord[IReaderExtensionTable.versionCode]
                                ) {
                                    this[IReaderExtensionTable.isObsolete] = false
                                }

                                when {
                                    foundExtension.versionCode > extensionRecord[IReaderExtensionTable.versionCode] -> {
                                        // There is an update
                                        this[IReaderExtensionTable.hasUpdate] = true
                                        updateMap.putIfAbsent(
                                            foundExtension.pkgName,
                                            IReaderExtensionDataClass(
                                                repo = foundExtension.repo,
                                                apkName = foundExtension.apkName,
                                                iconUrl = foundExtension.iconUrl,
                                                name = foundExtension.name,
                                                pkgName = foundExtension.pkgName,
                                                versionName = foundExtension.versionName,
                                                versionCode = foundExtension.versionCode,
                                                lang = foundExtension.lang,
                                                isNsfw = foundExtension.isNsfw,
                                                installed = true,
                                                hasUpdate = true,
                                                obsolete = false,
                                            ),
                                        )
                                    }
                                    foundExtension.versionCode < extensionRecord[IReaderExtensionTable.versionCode] -> {
                                        // Somehow the user installed an invalid version
                                        this[IReaderExtensionTable.isObsolete] = true
                                    }
                                }
                            }
                            execute(this@transaction)
                        }
                    }

                    // Fully update non-installed extensions
                    val extensionsToFullyUpdate = extensionsInstalled[false].orEmpty()
                    if (extensionsToFullyUpdate.isNotEmpty()) {
                        BatchUpdateStatement(IReaderExtensionTable).apply {
                            extensionsToFullyUpdate.forEach { (foundExtension, extensionRecord) ->
                                addBatch(EntityID(extensionRecord[IReaderExtensionTable.id].value, IReaderExtensionTable))
                                // Extension is not installed, so we can overwrite the data
                                this[IReaderExtensionTable.repo] = foundExtension.repo
                                this[IReaderExtensionTable.name] = foundExtension.name
                                this[IReaderExtensionTable.versionName] = foundExtension.versionName
                                this[IReaderExtensionTable.versionCode] = foundExtension.versionCode
                                this[IReaderExtensionTable.lang] = foundExtension.lang
                                this[IReaderExtensionTable.isNsfw] = foundExtension.isNsfw
                                this[IReaderExtensionTable.apkName] = foundExtension.apkName
                                this[IReaderExtensionTable.iconUrl] = foundExtension.iconUrl
                            }
                            execute(this@transaction)
                        }
                    }
                }

                // Insert new extensions
                if (extensionsToInsert.isNotEmpty()) {
                    IReaderExtensionTable.batchInsert(extensionsToInsert) { foundExtension ->
                        this[IReaderExtensionTable.repo] = foundExtension.repo
                        this[IReaderExtensionTable.name] = foundExtension.name
                        this[IReaderExtensionTable.pkgName] = foundExtension.pkgName
                        this[IReaderExtensionTable.versionName] = foundExtension.versionName
                        this[IReaderExtensionTable.versionCode] = foundExtension.versionCode
                        this[IReaderExtensionTable.lang] = foundExtension.lang
                        this[IReaderExtensionTable.isNsfw] = foundExtension.isNsfw
                        this[IReaderExtensionTable.apkName] = foundExtension.apkName
                        this[IReaderExtensionTable.iconUrl] = foundExtension.iconUrl
                    }
                }

                // Deal with obsolete extensions
                val extensionsToRemove = extensionsToDelete
                    .groupBy { it[IReaderExtensionTable.isInstalled] }
                    .mapValues { (_, extensions) -> extensions.map { it[IReaderExtensionTable.pkgName] } }

                // Not in the repo, so these extensions are obsolete
                val obsoleteExtensions = extensionsToRemove[true].orEmpty()
                if (obsoleteExtensions.isNotEmpty()) {
                    IReaderExtensionTable.update({ IReaderExtensionTable.pkgName inList obsoleteExtensions }) {
                        it[isObsolete] = true
                    }
                }

                // Not installed, so we can remove the record
                val removeExtensions = extensionsToRemove[false].orEmpty()
                if (removeExtensions.isNotEmpty()) {
                    IReaderExtensionTable.deleteWhere { IReaderExtensionTable.pkgName inList removeExtensions }
                }
            }
        }
    }
}
