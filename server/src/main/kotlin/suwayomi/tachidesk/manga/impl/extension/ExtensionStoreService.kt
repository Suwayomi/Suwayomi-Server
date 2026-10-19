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
import okhttp3.HttpUrl
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
import suwayomi.tachidesk.manga.impl.extension.github.BaseNetworkExtensionStore
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

    /**
     * `index_v2` redirect chains are store-controlled input; without a bound a
     * store could make this server follow (and fetch) an unbounded chain of
     * URLs — stack growth plus an SSRF springboard.
     */
    private const val MAX_INDEX_REDIRECTS = 3

    val network: NetworkHelper by injectLazy()
    val protoBuf: ProtoBuf by injectLazy()
    val json: Json by injectLazy()

    suspend fun fetch(indexUrl: String): ExtensionStore = fetch(indexUrl, redirectDepth = 0)

    private suspend fun fetch(
        indexUrl: String,
        redirectDepth: Int,
    ): ExtensionStore {
        // Egress policy: http(s) only + (unless enabled) no private-network
        // hosts — applied to every URL a store hands us, on every hop.
        val validatedIndexUrl = StoreUrlPolicy.requireAllowedUrl(indexUrl).toString()
        return try {
            val response = network.client.newCall(GET(validatedIndexUrl)).awaitSuccess()
            val contentType =
                response.body
                    .contentType()
                    ?.toString()
                    ?.lowercase()
            response.body.source().decompressIfGzipped().use { source ->
                val (networkStore, effectiveIndexUrl) = decodeIndex(source, validatedIndexUrl, contentType)

                if (networkStore is NetworkLegacyExtensionRepo && networkStore.indexV2 != null) {
                    if (redirectDepth >= MAX_INDEX_REDIRECTS) {
                        throw IllegalArgumentException(
                            "Extension store '$indexUrl' exceeded the index_v2 redirect limit ($MAX_INDEX_REDIRECTS)",
                        )
                    }
                    return fetch(networkStore.indexV2, redirectDepth + 1)
                }

                networkStore.toExtensionStore(effectiveIndexUrl)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.error(e) { "Failed to fetch extension store '$indexUrl'" }
            throw e
        }
    }

    /**
     * Decodes the store index. Content sniffing wins over transport metadata:
     * a payload whose first non-whitespace byte is `[`/`{` goes to the JSON
     * decoders — parse-confirmed on a peeked view (which never consumes
     * [source]) unless a marker already claims JSON. The peek-parse guard
     * protects protobuf, which can legitimately start with `0x5B`/`0x7B`
     * (valid field tags). Content-Type and the `.json` extension remain hints
     * for that disambiguation, and `octet-stream` in particular is only a
     * fallback signal for payloads without a JSON signature — hosts like
     * GitHub Releases or object stores serve JSON indexes as octet-stream, so
     * it must never force a real JSON document into the protobuf decoder.
     */
    private suspend fun decodeIndex(
        source: BufferedSource,
        indexUrl: String,
        contentType: String?,
    ): Pair<BaseNetworkExtensionStore, String> {
        val firstByte = source.peekFirstNonWhitespaceByte()
        val jsonByMarker =
            contentType?.contains("json") == true || indexUrl.endsWith(".json", ignoreCase = true)

        return when {
            // "[..." — a legacy repo serves the extension list itself; the store
            // metadata lives in repo.json next to index.min.json.
            firstByte == '['.code && (jsonByMarker || source.peekLooksLikeJsonArray()) -> {
                if (!indexUrl.endsWith("/index.min.json")) {
                    throw IllegalArgumentException("Provided legacy store url is not valid")
                }
                val repoUrl = StoreUrlPolicy.requireAllowedUrl(indexUrl.replace("/index.min.json", "/repo.json"))
                val store =
                    network.client.newCall(GET(repoUrl.toString())).awaitSuccess().body.source().use {
                        json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(it)
                    }
                store to repoUrl.toString()
            }

            // "{..." — either a legacy repo.json or a v2 JSON store index.
            firstByte == '{'.code && (jsonByMarker || source.peekLooksLikeJsonObject()) -> {
                try {
                    json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek()) to indexUrl
                } catch (_: IllegalArgumentException) {
                    json.decodeFromBufferedSource<NetworkExtensionStore>(source) to indexUrl
                }
            }

            // no JSON signature — protobuf, whether claimed by Content-Type
            // (protobuf/octet-stream) or simply unrecognized
            else -> {
                protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray()) to indexUrl
            }
        }
    }

    /**
     * First payload byte after JSON-insignificant whitespace, read on a peeked
     * view so [this] is not consumed. JSON documents may carry leading
     * whitespace — which is also made of valid protobuf field tags, so the
     * signature byte must skip it. Returns -1 when nothing follows.
     */
    private fun BufferedSource.peekFirstNonWhitespaceByte(): Int {
        val peeked = peek()
        while (peeked.request(1)) {
            val byte = peeked.readByte().toInt() and 0xFF
            if (byte != ' '.code && byte != '\t'.code && byte != '\n'.code && byte != '\r'.code) {
                return byte
            }
        }
        return -1
    }

    /** True when the payload parses as the legacy JSON extension list. */
    private fun BufferedSource.peekLooksLikeJsonArray(): Boolean =
        try {
            json.decodeFromBufferedSource<List<NetworkLegacyExtension>>(peek())
            true
        } catch (_: Exception) {
            false
        }

    /** True when the payload parses as a JSON object under either store schema. */
    private fun BufferedSource.peekLooksLikeJsonObject(): Boolean =
        try {
            json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(peek())
            true
        } catch (_: Exception) {
            false
        } ||
            try {
                json.decodeFromBufferedSource<NetworkExtensionStore>(peek())
                true
            } catch (_: Exception) {
                false
            }

    /**
     * Decodes a `{`-JSON-or-protobuf payload: tries JSON on a peeked view so
     * the original stream stays intact for the protobuf fallback. Used where
     * only a JSON object vs protobuf ambiguity exists.
     */
    private inline fun <reified T> BufferedSource.decodeJsonObjectOrProto(): T =
        if (peek().readByte() == '{'.code.toByte()) {
            try {
                json.decodeFromBufferedSource<T>(peek())
            } catch (_: Exception) {
                protoBuf.decodeFromByteArray(readByteArray())
            }
        } else {
            protoBuf.decodeFromByteArray(readByteArray())
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
                // store-supplied URL: same egress policy as the index fetch
                val listUrl: HttpUrl = StoreUrlPolicy.requireAllowedUrl(store.extensionListUrl)
                val response = network.client.newCall(GET(listUrl.toString())).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    source
                        .decodeJsonObjectOrProto<NetworkExtensionStore.ExtensionList>()
                        .toExtensionInfos(store)
                }
            } else if (!store.isLegacy) {
                val indexUrl = StoreUrlPolicy.requireAllowedUrl(store.indexUrl)
                val response = network.client.newCall(GET(indexUrl.toString())).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    source
                        .decodeJsonObjectOrProto<NetworkExtensionStore>()
                        .extensionList!!
                        .toExtensionInfos(store)
                }
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                val legacyIndexUrl = StoreUrlPolicy.requireAllowedUrl("$storeBaseUrl/index.min.json")
                val response = network.client.newCall(GET(legacyIndexUrl.toString())).awaitSuccess()
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
