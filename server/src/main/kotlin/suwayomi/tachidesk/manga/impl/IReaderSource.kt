package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import ireader.core.prefs.PreferenceStoreFactory
import ireader.core.source.Source
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
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

    private val sourceCache = mutableMapOf<Long, ireader.core.source.CatalogSource>()

    fun getSourceList(): List<IReaderSourceDataClass> =
        transaction {
            IReaderSourceTable.selectAll().map {
                val sourceExtension =
                    IReaderExtensionTable
                        .selectAll()
                        .where { IReaderExtensionTable.id eq it[IReaderSourceTable.extension] }
                        .first()

                val sourceId = it[IReaderSourceTable.id].value
                val catalogSource = getCatalogueSourceOrNull(sourceId)
                val baseUrl: String? = null // BaseUrl extraction would require reflection

                IReaderSourceDataClass(
                    id = sourceId.toString(),
                    name = it[IReaderSourceTable.name],
                    lang = it[IReaderSourceTable.lang],
                    iconUrl = getExtensionIconUrl(sourceExtension[IReaderExtensionTable.apkName]),
                    supportsLatest = true,
                    isConfigurable = true, // IReader sources have preferences via PreferenceStore
                    isNsfw = it[IReaderSourceTable.isNsfw],
                    displayName = catalogSource?.name ?: it[IReaderSourceTable.name],
                    baseUrl = baseUrl,
                )
            }
        }

    fun getSource(sourceId: Long): IReaderSourceDataClass? {
        return transaction {
            val source =
                IReaderSourceTable.selectAll().where { IReaderSourceTable.id eq sourceId }.firstOrNull()
                    ?: return@transaction null
            val extension =
                IReaderExtensionTable
                    .selectAll()
                    .where { IReaderExtensionTable.id eq source[IReaderSourceTable.extension] }
                    .first()

            val catalogSource = getCatalogueSourceOrNull(sourceId)
            val baseUrl: String? = null // BaseUrl extraction would require reflection

            IReaderSourceDataClass(
                id = sourceId.toString(),
                name = source[IReaderSourceTable.name],
                lang = source[IReaderSourceTable.lang],
                iconUrl = getExtensionIconUrl(extension[IReaderExtensionTable.apkName]),
                supportsLatest = true,
                isConfigurable = true, // IReader sources have preferences via PreferenceStore
                isNsfw = source[IReaderSourceTable.isNsfw],
                displayName = catalogSource?.name ?: source[IReaderSourceTable.name],
                baseUrl = baseUrl,
            )
        }
    }

    private fun implementsInterface(
        obj: Any,
        interfaceName: String,
    ): Boolean {
        fun checkClass(clazz: Class<*>?): Boolean {
            if (clazz == null) return false
            if (clazz.interfaces.any { it.name == interfaceName }) return true
            if (checkClass(clazz.superclass)) return true
            return clazz.interfaces.any { checkClass(it) }
        }
        return checkClass(obj.javaClass)
    }

    fun getCatalogueSourceOrNull(sourceId: Long): ireader.core.source.CatalogSource? {
        // Check cache first
        sourceCache[sourceId]?.let { return it }

        // Load source if not cached
        val sourceRecord =
            transaction {
                IReaderSourceTable.selectAll().where { IReaderSourceTable.id eq sourceId }.firstOrNull()
            } ?: return null

        val extensionId = sourceRecord[IReaderSourceTable.extension].value
        val extensionRecord =
            transaction {
                IReaderExtensionTable.selectAll().where { IReaderExtensionTable.id eq extensionId }.first()
            }

        val apkName = extensionRecord[IReaderExtensionTable.apkName]
        val pkgName = extensionRecord[IReaderExtensionTable.pkgName]
        val className = extensionRecord[IReaderExtensionTable.classFQName]

        val fileNameWithoutType = apkName.substringBefore(".apk")
        val jarPath = "${applicationDirs.extensionsRoot}/ireader/$fileNameWithoutType.jar"

        if (!File(jarPath).exists()) {
            logger.warn { "IReader extension jar not found: $jarPath" }
            return null
        }

        return try {
            // Load the extension with proper Dependencies
            val classLoader =
                PackageTools.jarLoaderMap[jarPath] ?: java.net.URLClassLoader(
                    arrayOf(
                        java.nio.file.Path
                            .of(jarPath)
                            .toUri()
                            .toURL(),
                    ),
                    this::class.java.classLoader,
                )
            val classToLoad = Class.forName(className, false, classLoader)
            PackageTools.jarLoaderMap[jarPath] = classLoader

            // Create Dependencies
            val preferences = PreferenceStoreFactory().create(pkgName)
            val httpClients = ireader.core.http.HttpClients(preferences)
            val dependencies = ireader.core.source.Dependencies(httpClients, preferences)

            // Instantiate the extension
            val extensionInstance =
                try {
                    classToLoad
                        .getDeclaredConstructor(ireader.core.source.Dependencies::class.java)
                        .newInstance(dependencies)
                } catch (e: NoSuchMethodException) {
                    classToLoad.getDeclaredConstructor().newInstance()
                }

            // Handle SourceFactory or Source
            val sourceInstances =
                when {
                    implementsInterface(extensionInstance, "ireader.core.source.SourceFactory") -> {
                        val method = extensionInstance.javaClass.getMethod("createSources")
                        method.invoke(extensionInstance) as List<*>
                    }
                    implementsInterface(extensionInstance, "ireader.core.source.Source") -> {
                        listOf(extensionInstance)
                    }
                    else -> {
                        logger.error { "Extension doesn't implement Source or SourceFactory: ${extensionInstance.javaClass.name}" }
                        return null
                    }
                }

            // Find the source with matching ID
            val sourceInstance =
                sourceInstances.firstOrNull { sourceObj ->
                    val idMethod = sourceObj?.javaClass?.getMethod("getId")
                    val id = idMethod?.invoke(sourceObj) as? Long
                    id == sourceId
                } ?: return null

            // Create a wrapper that implements CatalogSource using reflection
            val wrapper = IReaderSourceWrapper(sourceInstance)
            sourceCache[sourceId] = wrapper
            wrapper
        } catch (e: Exception) {
            logger.error(e) { "Failed to load IReader source $sourceId" }
            null
        }
    }

    fun unregisterCatalogueSource(sourceId: Long) {
        sourceCache.remove(sourceId)
    }
}
