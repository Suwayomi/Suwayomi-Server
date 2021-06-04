package suwayomi.tachidesk.anime.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.anime.impl.util.PackageTools.loadExtensionSources
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.anime.model.table.AnimeSourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import java.util.concurrent.ConcurrentHashMap

object GetAnimeHttpSource {
    private val sourceCache = ConcurrentHashMap<Long, AnimeHttpSource>()
    private val applicationDirs by DI.global.instance<ApplicationDirs>()

    fun getAnimeHttpSource(sourceId: Long): AnimeHttpSource {
        val cachedResult: AnimeHttpSource? = sourceCache[sourceId]
        if (cachedResult != null) {
            return cachedResult
        }

        val sourceRecord = transaction {
            AnimeSourceTable.select { AnimeSourceTable.id eq sourceId }.first()
        }

        val extensionId = sourceRecord[AnimeSourceTable.extension]
        val extensionRecord = transaction {
            AnimeExtensionTable.select { AnimeExtensionTable.id eq extensionId }.first()
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
}
