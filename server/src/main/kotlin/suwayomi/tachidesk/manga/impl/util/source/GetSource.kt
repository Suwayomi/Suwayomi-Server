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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.KeyedMutex
import suwayomi.tachidesk.manga.impl.util.PackageTools.loadExtensionSources
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

    // single-flight per source id: concurrent misses must not each run the expensive
    // jar load in loadExtensionSources; the refcounted entry is dropped when the
    // last holder or waiter leaves, keeping the map bounded
    private val loadMutexBySourceId = KeyedMutex<Long>()

    private suspend fun getSource(sourceId: Long): Source? {
        val cachedResult: Source? = sourceCache[sourceId]
        if (cachedResult != null) {
            return cachedResult
        }

        return loadMutexBySourceId.withLock(sourceId) {
            sourceCache[sourceId] ?: loadSource(sourceId)
        }
    }

    private suspend fun loadSource(sourceId: Long): Source? {
        val sourceRecord =
            transaction {
                SourceTable.selectAll().where { SourceTable.id eq sourceId }.firstOrNull()
            } ?: return null

        val extensionId = sourceRecord[SourceTable.extension]
        val extensionRecord =
            transaction {
                ExtensionTable.selectAll().where { ExtensionTable.id eq extensionId }.firstOrNull()
            } ?: return null

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
            // non-http sources (e.g. LocalSource) are handled by the caller; stub
            // sources are not online-capable — only HttpSource instances get cached
            if (it is HttpSource) {
                sourceCache[it.id] = it
            }
        }
        // the requested sourceId can be absent from the loaded list (e.g. a
        // SourceFactory that does not return it) — report null instead of `!!`
        return sourceCache[sourceId]
    }

    suspend fun getSourceOrNull(sourceId: Long): Source? =
        try {
            getSource(sourceId)
        } catch (e: Exception) {
            logger.warn(e) { "getCatalogueSource($sourceId) failed" }
            null
        }

    suspend fun getSourceOrStub(sourceId: Long): Source = getSourceOrNull(sourceId) ?: StubSource(sourceId)

    fun registerSource(sourcePair: Pair<Long, Source>) {
        sourceCache += sourcePair
    }

    fun unregisterSource(sourceId: Long) {
        sourceCache.remove(sourceId)
    }

    fun unregisterAllSources() {
        // 0L == LocalSource.ID: the built-in local source stays registered
        (sourceCache - 0L).forEach { (id, _) ->
            sourceCache.remove(id)
        }
    }
}
