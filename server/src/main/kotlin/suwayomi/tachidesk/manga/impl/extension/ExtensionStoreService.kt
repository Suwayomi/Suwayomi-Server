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
import okio.BufferedSource
import okio.buffer
import okio.gzip
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

    suspend fun fetch(indexUrl: String): ExtensionStore {
        var updatedIndexUrl: String = indexUrl
        return try {
            val response = network.client.newCall(GET(updatedIndexUrl)).awaitSuccess()
            response.body.source().decompressIfGzipped().use { source ->
                val networkStore =
                    when (source.peek().readByte()) {
                        // "[..."
                        0x5B.toByte() -> {
                            run {
                                if (!indexUrl.endsWith("/index.min.json")) {
                                    throw IllegalArgumentException("Provided legacy store url is not valid")
                                }
                                updatedIndexUrl = indexUrl.replace("/index.min.json", "/repo.json")
                                network.client.newCall(GET(updatedIndexUrl)).awaitSuccess().body.source().use {
                                    json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(it)
                                }
                            }
                        }

                        // "{..."
                        0x7B.toByte() -> {
                            try {
                                json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek())
                            } catch (_: IllegalArgumentException) {
                                json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                            }
                        }

                        else -> {
                            protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                        }
                    }

                if (networkStore is NetworkLegacyExtensionRepo && networkStore.indexV2 != null) {
                    return fetch(networkStore.indexV2)
                }

                networkStore.toExtensionStore(updatedIndexUrl)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.error(e) { "Failed to fetch extension store '$indexUrl'" }
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
                    it[extensionListUrl] = store.extensionListUrl
                }
            } else {
                ExtensionStoreTable.update({ ExtensionStoreTable.indexUrl eq store.indexUrl }) {
                    it[name] = store.name
                    it[badgeLabel] = store.badgeLabel
                    it[signingKey] = store.signingKey
                    it[contactWebsite] = store.contact.website
                    it[contactDiscord] = store.contact.discord
                    it[isLegacy] = store.isLegacy
                    it[extensionListUrl] = store.extensionListUrl
                }
            }
        }
    }

    suspend fun getAndRefresh(): List<ExtensionStore> {
        val stores =
            transaction {
                ExtensionStoreTable.selectAll().toList()
            }
        var needsPrefUpdate = false
        val updateStores =
            stores.mapNotNull { storeRow ->
                val oldIndexUrl = storeRow[ExtensionStoreTable.indexUrl]
                val oldName = storeRow[ExtensionStoreTable.name]
                try {
                    val store = fetch(oldIndexUrl)
                    if (store.indexUrl != oldIndexUrl) {
                        transaction {
                            ExtensionStoreTable.deleteWhere { ExtensionStoreTable.indexUrl eq oldIndexUrl }
                        }
                        needsPrefUpdate = true
                    }
                    upsert(store)
                    store
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to fetch extension store '$oldName ($oldIndexUrl)'" }
                    null
                }
            }
        if (needsPrefUpdate) syncDbToPrefs()
        return updateStores
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
        val toRemove = (dbStores.keys - prefUrls).toMutableSet()
        var needsPrefUpdate = toRemove.isNotEmpty()

        toAdd.forEach { url ->
            try {
                val store = fetch(url)
                if (store.indexUrl != url) {
                    transaction {
                        ExtensionStoreTable.deleteWhere { ExtensionStoreTable.indexUrl eq url }
                    }
                    needsPrefUpdate = true
                    toRemove -= store.indexUrl
                }
                upsert(store)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to sync preference store '$url' to database" }
            }
        }

        if (toRemove.isNotEmpty()) {
            transaction {
                ExtensionStoreTable.deleteWhere { ExtensionStoreTable.indexUrl inList toRemove.toList() }
            }
        }
        if (needsPrefUpdate) {
            syncDbToPrefs()
        }
    }

    suspend fun getExtensions(store: ExtensionStore): List<ExtensionInfo> {
        val extensions =
            if (store.extensionListUrl != null) {
                val response = network.client.newCall(GET(store.extensionListUrl)).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> {
                            json.decodeFromBufferedSource<NetworkExtensionStore.ExtensionList>(source)
                        }

                        else -> {
                            protoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(
                                source.readByteArray(),
                            )
                        }
                    }.toExtensionInfos(store)
                }
            } else if (!store.isLegacy) {
                val response = network.client.newCall(GET(store.indexUrl)).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore>(source)

                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                    }.extensionList!!
                        .toExtensionInfos(store)
                }
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

    private fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip =
            peek().use { peeked ->
                try {
                    peeked.readShort().toInt() == 0x1f8b
                } catch (_: Exception) {
                    false
                }
            }

        return if (isGzip) gzip().buffer() else this
    }
}
