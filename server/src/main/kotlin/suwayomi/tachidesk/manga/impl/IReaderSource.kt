package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.ireader.core_api.source.CatalogSource
import org.ireader.core_api.source.HttpSource
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtension.getExtensionIconUrl
import suwayomi.tachidesk.manga.impl.util.PackageTools
import suwayomi.tachidesk.manga.model.dataclass.IReaderSourceDataClass
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File

object IReaderSource {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()
    
    private val sourceCache = mutableMapOf<Long, CatalogSource>()

    fun getSourceList(): List<IReaderSourceDataClass> {
        return transaction {
            IReaderSourceTable.selectAll().mapNotNull {
                val source = getCatalogueSourceOrNull(it[IReaderSourceTable.id].value) ?: return@mapNotNull null
                val sourceExtension = IReaderExtensionTable.selectAll()
                    .where { IReaderExtensionTable.id eq it[IReaderSourceTable.extension] }.first()

                IReaderSourceDataClass(
                    id = it[IReaderSourceTable.id].value.toString(),
                    name = it[IReaderSourceTable.name],
                    lang = it[IReaderSourceTable.lang],
                    iconUrl = getExtensionIconUrl(sourceExtension[IReaderExtensionTable.apkName]),
                    supportsLatest = true,
                    isConfigurable = false,
                    isNsfw = it[IReaderSourceTable.isNsfw],
                    displayName = source.toString(),
                    baseUrl = runCatching { (source as? HttpSource)?.baseUrl }.getOrNull(),
                )
            }
        }
    }

    fun getSource(sourceId: Long): IReaderSourceDataClass? {
        return transaction {
            val source = IReaderSourceTable.selectAll().where { IReaderSourceTable.id eq sourceId }.firstOrNull() 
                ?: return@transaction null
            val catalogueSource = getCatalogueSourceOrNull(sourceId) ?: return@transaction null
            val extension = IReaderExtensionTable.selectAll()
                .where { IReaderExtensionTable.id eq source[IReaderSourceTable.extension] }.first()

            IReaderSourceDataClass(
                id = sourceId.toString(),
                name = source[IReaderSourceTable.name],
                lang = source[IReaderSourceTable.lang],
                iconUrl = getExtensionIconUrl(extension[IReaderExtensionTable.apkName]),
                supportsLatest = true,
                isConfigurable = false,
                isNsfw = source[IReaderSourceTable.isNsfw],
                displayName = catalogueSource.toString(),
                baseUrl = runCatching { (catalogueSource as? HttpSource)?.baseUrl }.getOrNull(),
            )
        }
    }

    fun getCatalogueSourceOrNull(sourceId: Long): CatalogSource? {
        return sourceCache.getOrPut(sourceId) {
            val sourceRecord = transaction {
                IReaderSourceTable.selectAll().where { IReaderSourceTable.id eq sourceId }.firstOrNull()
            } ?: return null

            val extensionId = sourceRecord[IReaderSourceTable.extension].value
            val extensionRecord = transaction {
                IReaderExtensionTable.selectAll().where { IReaderExtensionTable.id eq extensionId }.first()
            }

            val apkName = extensionRecord[IReaderExtensionTable.apkName]
            val className = extensionRecord[IReaderExtensionTable.classFQName]
            
            val fileNameWithoutType = apkName.substringBefore(".apk")
            val jarPath = "${applicationDirs.extensionsRoot}/ireader/$fileNameWithoutType.jar"

            if (!File(jarPath).exists()) {
                logger.warn { "IReader extension jar not found: $jarPath" }
                return null
            }

            try {
                val extensionInstance = PackageTools.loadExtensionSources(jarPath, className)
                val sources: List<CatalogSource> = when (extensionInstance) {
                    is org.ireader.core_api.source.Source -> listOf(extensionInstance as CatalogSource)
                    is org.ireader.core_api.source.SourceFactory -> 
                        extensionInstance.createSources().map { it as CatalogSource }
                    else -> emptyList()
                }

                sources.firstOrNull { it.id == sourceId }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load IReader source $sourceId" }
                null
            }
        }
    }

    fun unregisterCatalogueSource(sourceId: Long) {
        sourceCache.remove(sourceId)
    }
}
