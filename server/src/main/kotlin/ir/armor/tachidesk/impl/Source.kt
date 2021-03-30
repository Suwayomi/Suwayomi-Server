package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.model.database.ExtensionTable
import ir.armor.tachidesk.model.database.SourceTable
import ir.armor.tachidesk.model.dataclass.SourceDataClass
import ir.armor.tachidesk.server.applicationDirs
import mu.KotlinLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

private val sourceCache = ConcurrentHashMap<Long, HttpSource>()


fun getHttpSource(sourceId: Long): HttpSource {
    val cachedResult: HttpSource? = sourceCache[sourceId]
    if (cachedResult != null) {
        logger.debug("used cached HttpSource: ${cachedResult.name}")
        return cachedResult
    }

    transaction {
        val sourceRecord = SourceTable.select { SourceTable.id eq sourceId }.firstOrNull()!!

        val extensionId = sourceRecord[SourceTable.extension]
        val extensionRecord = ExtensionTable.select { ExtensionTable.id eq extensionId }.firstOrNull()!!
        val apkName = extensionRecord[ExtensionTable.apkName]
        val className = extensionRecord[ExtensionTable.classFQName]
        val jarName = apkName.substringBefore(".apk") + ".jar"
        val jarPath = "${applicationDirs.extensionsRoot}/$jarName"

        val extensionInstance = loadExtensionInstance(jarPath,className)

        if (sourceRecord[SourceTable.partOfFactorySource]) {
            (extensionInstance as SourceFactory).createSources().forEach{
                sourceCache[it.id] = it as HttpSource
            }
        } else {
            (extensionInstance as HttpSource).also {
                sourceCache[it.id] = it
            }
        }
    }
    return sourceCache[sourceId]!!
}

fun getSourceList(): List<SourceDataClass> {
    return transaction {
        return@transaction SourceTable.selectAll().map {
            SourceDataClass(
                it[SourceTable.id].value.toString(),
                it[SourceTable.name],
                it[SourceTable.lang],
                getExtensionIconUrl(ExtensionTable.select { ExtensionTable.id eq it[SourceTable.extension] }.first()[ExtensionTable.apkName]),
                getHttpSource(it[SourceTable.id].value).supportsLatest
            )
        }
    }
}

fun getSource(sourceId: Long): SourceDataClass {
    return transaction {
        val source = SourceTable.select { SourceTable.id eq sourceId }.firstOrNull()

        return@transaction SourceDataClass(
            sourceId.toString(),
            source?.get(SourceTable.name),
            source?.get(SourceTable.lang),
            source?.let { ExtensionTable.select { ExtensionTable.id eq source[SourceTable.extension] }.first()[ExtensionTable.iconUrl] },
            source?.let { getHttpSource(sourceId).supportsLatest }
        )
    }
}
