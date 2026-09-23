package suwayomi.tachidesk.manga.impl.util.source

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.extension.lnreader.LnReaderPluginStore
import suwayomi.tachidesk.manga.impl.extension.lnreader.LnReaderSource
import suwayomi.tachidesk.manga.impl.util.PackageTools.loadExtensionSources
import suwayomi.tachidesk.manga.model.dataclass.ExtensionKind
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.div

object GetSource {
    private val logger = KotlinLogging.logger { }

    private val sourceCache = ConcurrentHashMap<Long, Source>()
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val lnReaderSourceColdLoadMutex = Mutex()

    private suspend fun getSource(sourceId: Long): Source? {
        val cachedResult: Source? = sourceCache[sourceId]
        if (cachedResult != null) {
            return cachedResult
        }

        val sourceRecord =
            transaction {
                SourceTable.selectAll().where { SourceTable.id eq sourceId }.firstOrNull()
            } ?: return null

        val extensionId = sourceRecord[SourceTable.extension]
        val extensionRecord =
            transaction {
                ExtensionTable.selectAll().where { ExtensionTable.id eq extensionId }.first()
            }

        if (extensionRecord[ExtensionTable.runtimeKind] == ExtensionKind.LNREADER.name) {
            return lnReaderSourceColdLoadMutex.withLock {
                sourceCache[sourceId]?.let { return@withLock it }

                val pluginId =
                    requireNotNull(extensionRecord[ExtensionTable.pluginId]) {
                        "Missing pluginId for LNReader extension"
                    }
                val installedPlugin =
                    LnReaderPluginStore.active(pluginId, Path(applicationDirs.extensionsRoot))
                        ?: throw IllegalStateException("Active LNReader plugin '$pluginId' not found")
                val source = LnReaderSource(installedPlugin)
                sourceCache[sourceId] = source
                source
            }
        }

        val apkName =
            extensionRecord[ExtensionTable.apkName]
                ?: throw NullPointerException("Missing apkName")
        val className = extensionRecord[ExtensionTable.classFQName]
        val jarName = apkName.substringBefore(".apk") + ".jar"
        val jarPath = Path(applicationDirs.extensionsRoot) / jarName

        when (val instance = loadExtensionSources(jarPath, className)) {
            is Source -> listOf(instance)
            is SourceFactory -> instance.createSources()
            else -> throw Exception("Unknown source class type! ${instance.javaClass}")
        }.forEach {
            sourceCache[it.id] = it as HttpSource
        }
        return sourceCache[sourceId]!!
    }

    suspend fun getSourceOrNull(sourceId: Long): Source? =
        try {
            getSource(sourceId)
        } catch (e: Exception) {
            logger.warn(e) { "getCatalogueSource($sourceId) failed" }
            null
        }

    suspend fun getSourceOrStub(sourceId: Long): Source = getSourceOrNull(sourceId) ?: StubSource(sourceId)

    fun getCachedSourceOrNull(sourceId: Long): Source? = sourceCache[sourceId]

    fun registerSource(sourcePair: Pair<Long, Source>) {
        sourceCache += sourcePair
    }

    suspend fun registerSource(sourceId: Long) {
        unregisterSource(sourceId)
        getSourceOrNull(sourceId)
    }

    fun unregisterSource(sourceId: Long) {
        (sourceCache.remove(sourceId) as? AutoCloseable)?.close()
    }

    fun unregisterAllSources() {
        (sourceCache - 0L).forEach { (id, _) ->
            unregisterSource(id)
        }
    }
}
