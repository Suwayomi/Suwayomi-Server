package ir.armor.tachidesk.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.impl.util.PackageTools.loadExtensionSources
import ir.armor.tachidesk.model.database.ExtensionTable
import ir.armor.tachidesk.model.database.SourceTable
import ir.armor.tachidesk.server.ApplicationDirs
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap

object GetHttpSource {
    private val sourceCache = ConcurrentHashMap<Long, HttpSource>()

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
        val jarPath = "${ApplicationDirs.extensionsRoot}/$jarName"

        val extensionInstance = loadExtensionSources(jarPath, className)

        if (sourceRecord[SourceTable.partOfFactorySource]) {
            (extensionInstance as SourceFactory).createSources().forEach {
                sourceCache[it.id] = it as HttpSource
            }
        } else {
            (extensionInstance as HttpSource).also {
                sourceCache[it.id] = it
            }
        }
        return sourceCache[sourceId]!!
    }
}
