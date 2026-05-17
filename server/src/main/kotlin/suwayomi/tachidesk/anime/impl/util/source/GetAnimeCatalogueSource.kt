package suwayomi.tachidesk.anime.impl.util.source

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.anime.model.table.AnimeSourceTable
import suwayomi.tachidesk.manga.impl.util.PackageTools.loadExtensionSources
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

object GetAnimeCatalogueSource {
    private val logger = KotlinLogging.logger { }

    private val sourceCache = ConcurrentHashMap<Long, AnimeCatalogueSource>()
    private val applicationDirs: ApplicationDirs by injectLazy()

    private fun getCatalogueSource(sourceId: Long): AnimeCatalogueSource? {
        val cachedResult: AnimeCatalogueSource? = sourceCache[sourceId]
        if (cachedResult != null) {
            return cachedResult
        }

        val sourceRecord =
            transaction {
                AnimeSourceTable.selectAll().where { AnimeSourceTable.id eq sourceId }.firstOrNull()
            } ?: return null

        val extensionId = sourceRecord[AnimeSourceTable.extension]
        val extensionRecord =
            transaction {
                AnimeExtensionTable.selectAll().where { AnimeExtensionTable.id eq extensionId }.first()
            }

        val apkName = extensionRecord[AnimeExtensionTable.apkName]
        val className = extensionRecord[AnimeExtensionTable.classFQName]
        val jarName = apkName.substringBefore(".apk") + ".jar"
        val jarPath = "${applicationDirs.extensionsRoot}/$jarName"

        when (val instance = loadExtensionSources(jarPath, className)) {
            is AnimeSource -> listOf(instance)
            is AnimeSourceFactory -> instance.createSources()
            else -> throw Exception("Unknown source class type! ${instance.javaClass}")
        }.forEach {
            sourceCache[it.id] = it as AnimeHttpSource
        }
        return sourceCache[sourceId]!!
    }

    fun getCatalogueSourceOrNull(sourceId: Long): AnimeCatalogueSource? =
        try {
            getCatalogueSource(sourceId)
        } catch (e: Exception) {
            logger.warn(e) { "getCatalogueSource($sourceId) failed" }
            null
        }

    fun getCatalogueSourceOrStub(sourceId: Long): AnimeCatalogueSource = getCatalogueSourceOrNull(sourceId) ?: AnimeStubSource(sourceId)

    fun registerCatalogueSource(sourcePair: Pair<Long, AnimeCatalogueSource>) {
        sourceCache += sourcePair
    }

    fun unregisterCatalogueSource(sourceId: Long) {
        sourceCache.remove(sourceId)
    }

    fun unregisterAllCatalogueSources() {
        (sourceCache - 0L).forEach { (id, _) ->
            sourceCache.remove(id)
        }
    }
}
