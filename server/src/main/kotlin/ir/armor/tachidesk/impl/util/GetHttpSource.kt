package ir.armor.tachidesk.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.impl.util.PackageTools.loadExtensionSources
import ir.armor.tachidesk.model.database.table.ExtensionTable
import ir.armor.tachidesk.model.database.table.SourceTable
import ir.armor.tachidesk.server.ApplicationDirs
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import java.util.concurrent.ConcurrentHashMap

object GetHttpSource {
    private val sourceCache = ConcurrentHashMap<Long, HttpSource>()
    private val applicationDirs by DI.global.instance<ApplicationDirs>()

    fun getHttpSource(sourceId: Long): HttpSource {
        val cachedResult: HttpSource? = sourceCache[sourceId]
        if (cachedResult != null) {
            return cachedResult
        }

        val sourceRecord = transaction {
            SourceTable.select { SourceTable.id eq sourceId }.firstOrNull()!!
        }

        val extensionId = sourceRecord[SourceTable.extension]
        val extensionRecord = transaction {
            ExtensionTable.select { ExtensionTable.id eq extensionId }.firstOrNull()!!
        }

        val apkName = extensionRecord[ExtensionTable.apkName]
        val className = extensionRecord[ExtensionTable.classFQName]
        val jarName = apkName.substringBefore(".apk") + ".jar"
        val jarPath = "${applicationDirs.extensionsRoot}/$jarName"

        when (val instance = loadExtensionSources(jarPath, className)) {
            is Source -> listOf(instance)
            is SourceFactory -> instance.createSources()
            else -> throw Exception("Unknown source class type! ${instance.javaClass}")
        }.forEach {
            sourceCache[it.id] = it as HttpSource
        }
        return sourceCache[sourceId]!!
    }
}
