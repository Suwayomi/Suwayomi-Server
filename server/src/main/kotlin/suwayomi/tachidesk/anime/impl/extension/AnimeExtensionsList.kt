package suwayomi.tachidesk.anime.impl.extension

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
import suwayomi.tachidesk.anime.impl.extension.AnimeExtension.getExtensionIconUrl
import suwayomi.tachidesk.anime.impl.util.AnimePackageTools.LIB_VERSION_MAX
import suwayomi.tachidesk.anime.impl.util.AnimePackageTools.LIB_VERSION_MIN
import suwayomi.tachidesk.anime.model.dataclass.AnimeExtensionDataClass
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.manga.impl.extension.github.ExtensionGithubApi
import suwayomi.tachidesk.manga.impl.extension.github.OnlineExtension
import suwayomi.tachidesk.manga.impl.extension.repoMatchRegex
import suwayomi.tachidesk.server.serverConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

object AnimeExtensionsList {
    private val logger = KotlinLogging.logger {}

    var lastUpdateCheck: Long = 0
    var updateMap = ConcurrentHashMap<String, OnlineExtension>()

    suspend fun fetchExtensions() {
        // update if 60 seconds has passed or requested offline and database is empty
        val extensionRepos =
            serverConfig.animeExtensionRepos.value.ifEmpty { serverConfig.extensionRepos.value }

        val extensions =
            extensionRepos.map { repo ->
                kotlin
                    .runCatching {
                        ExtensionGithubApi.findExtensions(repo.repoUrlReplace(), LIB_VERSION_MIN, LIB_VERSION_MAX)
                    }.onFailure {
                        logger.warn(it) {
                            "Failed to fetch anime extensions for repo: $repo"
                        }
                    }
            }
        val foundExtensions = extensions.mapNotNull { it.getOrNull() }.flatten()
        updateExtensionDatabase(foundExtensions)
    }

    suspend fun fetchExtensionsCached() {
        // update if 60 seconds has passed or requested offline and database is empty
        if (lastUpdateCheck + 60.seconds.inWholeMilliseconds < System.currentTimeMillis()) {
            logger.debug { "Getting anime extensions list from the internet" }
            lastUpdateCheck = System.currentTimeMillis()

            fetchExtensions()
        } else {
            logger.debug { "used cached anime extension list" }
        }
    }

    suspend fun getExtensionList(): List<AnimeExtensionDataClass> {
        fetchExtensionsCached()
        return extensionTableAsDataClass()
    }

    fun extensionTableAsDataClass() =
        transaction {
            AnimeExtensionTable.selectAll().map {
                AnimeExtensionDataClass(
                    it[AnimeExtensionTable.repo],
                    it[AnimeExtensionTable.apkName],
                    getExtensionIconUrl(it[AnimeExtensionTable.apkName]),
                    it[AnimeExtensionTable.name],
                    it[AnimeExtensionTable.pkgName],
                    it[AnimeExtensionTable.versionName],
                    it[AnimeExtensionTable.versionCode],
                    it[AnimeExtensionTable.lang],
                    it[AnimeExtensionTable.isNsfw],
                    it[AnimeExtensionTable.isInstalled],
                    it[AnimeExtensionTable.hasUpdate],
                    it[AnimeExtensionTable.isObsolete],
                )
            }
        }

    private val updateExtensionDatabaseMutex = Mutex()

    private suspend fun updateExtensionDatabase(foundExtensions: List<OnlineExtension>) {
        updateExtensionDatabaseMutex.withLock {
            transaction {
                val uniqueExtensions =
                    foundExtensions
                        .groupBy { it.pkgName }
                        .mapValues { (_, extension) ->
                            extension.maxBy { it.versionCode }
                        }.values
                val installedExtensions =
                    AnimeExtensionTable
                        .selectAll()
                        .toList()
                        .associateBy { it[AnimeExtensionTable.pkgName] }
                val extensionsToUpdate = mutableListOf<Pair<OnlineExtension, ResultRow>>()
                val extensionsToInsert = mutableListOf<OnlineExtension>()
                val extensionsToDelete =
                    installedExtensions.filter { it.value[AnimeExtensionTable.repo] != null }.mapNotNull { (pkgName, extension) ->
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
                            .groupBy { it.second[AnimeExtensionTable.isInstalled] }
                    val installedExtensionsToUpdate = extensionsInstalled[true].orEmpty()
                    if (installedExtensionsToUpdate.isNotEmpty()) {
                        BatchUpdateStatement(AnimeExtensionTable).apply {
                            installedExtensionsToUpdate.forEach { (foundExtension, extensionRecord) ->
                                addBatch(EntityID(extensionRecord[AnimeExtensionTable.id].value, AnimeExtensionTable))
                                // Always update icon url and repo
                                this[AnimeExtensionTable.iconUrl] = foundExtension.iconUrl
                                this[AnimeExtensionTable.repo] = foundExtension.repo

                                // add these because batch updates need matching columns
                                this[AnimeExtensionTable.hasUpdate] = extensionRecord[AnimeExtensionTable.hasUpdate]
                                this[AnimeExtensionTable.isObsolete] = extensionRecord[AnimeExtensionTable.isObsolete]

                                // a previously removed extension is now available again
                                if (extensionRecord[AnimeExtensionTable.isObsolete] &&
                                    foundExtension.versionCode >= extensionRecord[AnimeExtensionTable.versionCode]
                                ) {
                                    this[AnimeExtensionTable.isObsolete] = false
                                }

                                when {
                                    foundExtension.versionCode > extensionRecord[AnimeExtensionTable.versionCode] -> {
                                        // there is an update
                                        this[AnimeExtensionTable.hasUpdate] = true
                                        updateMap.putIfAbsent(foundExtension.pkgName, foundExtension)
                                    }

                                    foundExtension.versionCode < extensionRecord[AnimeExtensionTable.versionCode] -> {
                                        // somehow the user installed an invalid version
                                        this[AnimeExtensionTable.isObsolete] = true
                                    }
                                }
                            }
                            execute(this@transaction)
                        }
                    }
                    val extensionsToFullyUpdate = extensionsInstalled[false].orEmpty()
                    if (extensionsToFullyUpdate.isNotEmpty()) {
                        BatchUpdateStatement(AnimeExtensionTable).apply {
                            extensionsToFullyUpdate.forEach { (foundExtension, extensionRecord) ->
                                addBatch(EntityID(extensionRecord[AnimeExtensionTable.id].value, AnimeExtensionTable))
                                // extension is not installed, so we can overwrite the data without a care
                                this[AnimeExtensionTable.repo] = foundExtension.repo
                                this[AnimeExtensionTable.name] = foundExtension.name
                                this[AnimeExtensionTable.versionName] = foundExtension.versionName
                                this[AnimeExtensionTable.versionCode] = foundExtension.versionCode
                                this[AnimeExtensionTable.lang] = foundExtension.lang
                                this[AnimeExtensionTable.isNsfw] = foundExtension.isNsfw
                                this[AnimeExtensionTable.apkName] = foundExtension.apkName
                                this[AnimeExtensionTable.iconUrl] = foundExtension.iconUrl
                            }
                            execute(this@transaction)
                        }
                    }
                }
                if (extensionsToInsert.isNotEmpty()) {
                    AnimeExtensionTable.batchInsert(extensionsToInsert) { foundExtension ->
                        this[AnimeExtensionTable.repo] = foundExtension.repo
                        this[AnimeExtensionTable.name] = foundExtension.name
                        this[AnimeExtensionTable.pkgName] = foundExtension.pkgName
                        this[AnimeExtensionTable.versionName] = foundExtension.versionName
                        this[AnimeExtensionTable.versionCode] = foundExtension.versionCode
                        this[AnimeExtensionTable.lang] = foundExtension.lang
                        this[AnimeExtensionTable.isNsfw] = foundExtension.isNsfw
                        this[AnimeExtensionTable.apkName] = foundExtension.apkName
                        this[AnimeExtensionTable.iconUrl] = foundExtension.iconUrl
                    }
                }

                // deal with obsolete extensions
                val extensionsToRemove =
                    extensionsToDelete
                        .groupBy { it[AnimeExtensionTable.isInstalled] }
                        .mapValues { (_, extensions) -> extensions.map { it[AnimeExtensionTable.pkgName] } }
                // not in the repo, so these extensions are obsolete
                val obsoleteExtensions = extensionsToRemove[true].orEmpty()
                if (obsoleteExtensions.isNotEmpty()) {
                    AnimeExtensionTable.update({ AnimeExtensionTable.pkgName inList obsoleteExtensions }) {
                        it[isObsolete] = true
                    }
                }
                // is not installed, so we can remove the record without a care
                val removeExtensions = extensionsToRemove[false].orEmpty()
                if (removeExtensions.isNotEmpty()) {
                    AnimeExtensionTable.deleteWhere { AnimeExtensionTable.pkgName inList removeExtensions }
                }
            }
        }
    }

    private fun String.repoUrlReplace(): String =
        if (contains("github")) {
            replace(repoMatchRegex) {
                "https://raw.githubusercontent.com/${it.groupValues[2]}/${it.groupValues[3]}/" +
                    (it.groupValues.getOrNull(4)?.ifBlank { null } ?: "repo") +
                    "/" +
                    (it.groupValues.getOrNull(5)?.ifBlank { null } ?: "index.min.json")
            }
        } else {
            this
        }
}
