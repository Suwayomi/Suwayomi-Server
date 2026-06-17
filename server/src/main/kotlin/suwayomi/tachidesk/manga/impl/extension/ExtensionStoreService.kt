package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.extension.github.NetworkExtensionStore
import suwayomi.tachidesk.manga.impl.extension.github.NetworkLegacyExtension
import suwayomi.tachidesk.manga.impl.extension.github.NetworkLegacyExtensionRepo
import suwayomi.tachidesk.manga.impl.extension.github.toExtensionInfo
import suwayomi.tachidesk.manga.impl.extension.github.toExtensionInfos
import suwayomi.tachidesk.manga.model.dataclass.ExtensionInfo
import suwayomi.tachidesk.manga.model.dataclass.ExtensionStore
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import kotlin.coroutines.cancellation.CancellationException

object ExtensionStoreService {
    private val logger = KotlinLogging.logger {}

    val network: NetworkHelper by injectLazy()
    val protoBuf: ProtoBuf by injectLazy()
    val json: Json by injectLazy()

    suspend fun fetch(indexUrl: String): ExtensionStore = fetch(indexUrl, forceV2 = false)

    private suspend fun fetch(
        indexUrl: String,
        forceV2: Boolean,
    ): ExtensionStore {
        var updatedIndexUrl = indexUrl
        return try {
            val response = network.client.newCall(GET(indexUrl)).awaitSuccess()
            response.body
                .source()
                .use { source ->
                    try {
                        protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.peek().readByteArray())
                    } catch (e: IllegalArgumentException) {
                        logger.debug { "Failed to decode as protobuf, trying JSON" }
                        if (forceV2) throw e
                        try {
                            json.decodeFromBufferedSource<NetworkExtensionStore>(source.peek())
                        } catch (_: IllegalArgumentException) {
                            logger.debug { "Failed to decode as NetworkExtensionStore, trying LegacyExtensionRepo" }
                            val legacyIndex =
                                try {
                                    json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek())
                                } catch (e: IllegalArgumentException) {
                                    if (!indexUrl.endsWith("/index.min.json")) {
                                        throw e
                                    }
                                    logger.debug { "Retrying with /index.min.json" }
                                    updatedIndexUrl = indexUrl.replace("/index.min.json", "/repo.json")
                                    network.client.newCall(GET(updatedIndexUrl)).awaitSuccess().body.source().use {
                                        json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(it)
                                    }
                                }

                            if (legacyIndex.indexV2 != null) {
                                return fetch(legacyIndex.indexV2, forceV2 = true)
                            } else {
                                legacyIndex
                            }
                        }
                    }
                }.toExtensionStore(updatedIndexUrl)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.debug(e) { "Failed to fetch extension store '$indexUrl'" }
            throw e
        }
    }

    fun upsert(store: ExtensionStore) {
        transaction {
            val existing =
                ExtensionStoreTable
                    .selectAll()
                    .where { ExtensionStoreTable.indexUrl eq store.indexUrl }
                    .firstOrNull()

            if (existing == null) {
                ExtensionStoreTable.insert {
                    it[name] = store.name
                    it[badgeLabel] = store.badgeLabel
                    it[signingKey] = store.signingKey
                    it[contactWebsite] = store.contact.website
                    it[contactDiscord] = store.contact.discord
                    it[indexUrl] = store.indexUrl
                    it[isLegacy] = store.isLegacy
                }
            } else {
                ExtensionStoreTable.update({ ExtensionStoreTable.indexUrl eq store.indexUrl }) {
                    it[name] = store.name
                    it[badgeLabel] = store.badgeLabel
                    it[signingKey] = store.signingKey
                    it[contactWebsite] = store.contact.website
                    it[contactDiscord] = store.contact.discord
                    it[isLegacy] = store.isLegacy
                }
            }
        }
    }

    suspend fun getAndRefresh(): List<ExtensionStore> {
        val stores =
            transaction {
                ExtensionStoreTable.selectAll().toList()
            }
        return stores.mapNotNull { storeRow ->
            val oldIndexUrl = storeRow[ExtensionStoreTable.indexUrl]
            val oldName = storeRow[ExtensionStoreTable.name]
            try {
                val store = fetch(oldIndexUrl)
                upsert(store)
                if (store.indexUrl != oldIndexUrl) {
                    transaction {
                        ExtensionStoreTable.deleteWhere { ExtensionStoreTable.indexUrl eq oldIndexUrl }
                    }
                    syncDbToPrefs()
                }
                store
            } catch (e: Exception) {
                logger.warn(e) { "Failed to fetch extension store '$oldName ($oldIndexUrl)'" }
                null
            }
        }
    }

    fun syncDbToPrefs() {
        val dbStores =
            transaction {
                ExtensionStoreTable
                    .selectAll()
                    .map { it[ExtensionStoreTable.indexUrl] }
                    .toSet()
            }

        val currentPrefs = serverConfig.extensionStores.value.toSet()
        val toAdd = dbStores - currentPrefs
        val toRemove = currentPrefs - dbStores

        if (toAdd.isNotEmpty()) {
            serverConfig.extensionStores.value = (serverConfig.extensionStores.value + toAdd).distinct()
        }

        if (toRemove.isNotEmpty()) {
            serverConfig.extensionStores.value = serverConfig.extensionStores.value.filterNot { it in toRemove }
        }
    }

    suspend fun syncPrefsToDb() {
        val prefUrls = serverConfig.extensionStores.value.toSet()

        val dbStores =
            transaction {
                ExtensionStoreTable.selectAll().associateBy { it[ExtensionStoreTable.indexUrl] }
            }

        val toAdd = prefUrls - dbStores.keys

        toAdd.forEach { url ->
            try {
                val store = fetch(url)
                upsert(store)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to sync preference store '$url' to database" }
            }
        }

        val toRemove = dbStores.keys - prefUrls
        if (toRemove.isNotEmpty()) {
            transaction {
                ExtensionStoreTable.deleteWhere { ExtensionStoreTable.indexUrl inList toRemove.toList() }
            }
        }
    }

    suspend fun getExtensions(store: ExtensionStore): List<ExtensionInfo> {
        val extensions =
            if (!store.isLegacy) {
                val response = network.client.newCall(GET(store.indexUrl)).awaitSuccess()
                response.body
                    .source()
                    .use { source ->
                        try {
                            protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.peek().readByteArray())
                        } catch (_: IllegalArgumentException) {
                            json.decodeFromBufferedSource<NetworkExtensionStore>(source.peek())
                        }
                    }.toExtensionInfos(store)
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                val response = network.client.newCall(GET("$storeBaseUrl/index.min.json")).awaitSuccess()
                response.body.source().use { source ->
                    json
                        .decodeFromBufferedSource<List<NetworkLegacyExtension>>(source)
                        .map { it.toExtensionInfo(store, storeBaseUrl) }
                }
            }
        return extensions
    }
}
