package suwayomi.tachidesk.manga.impl.extension.ireader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import ireader.core.source.Source
import ireader.core.source.SourceFactory
import okhttp3.CacheControl
import okio.buffer
import okio.sink
import okio.source
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.extension.ExtensionInstaller
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtensionsList.extensionTableAsDataClass
import suwayomi.tachidesk.manga.impl.util.PackageTools
import suwayomi.tachidesk.manga.impl.util.PackageTools.getPackageInfo
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream

object IReaderExtension {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()

    private const val EXTENSION_FEATURE = "ireader"
    private const val METADATA_SOURCE_CLASS = "source.class"
    private const val METADATA_NSFW = "source.nsfw"
    private const val METADATA_DESCRIPTION = "source.description"
    private const val METADATA_ICON = "source.icon"
    private const val LIB_VERSION_MIN = 1
    private const val LIB_VERSION_MAX = 1

    suspend fun installExtension(pkgName: String): Int {
        logger.debug { "Installing IReader extension $pkgName" }
        val extensionRecord = extensionTableAsDataClass().first { it.pkgName == pkgName }

        return installAPK {
            val repo = extensionRecord.repo ?: throw NullPointerException("Could not find extension repo")
            val apkURL = IReaderGithubApi.getApkUrl(repo, extensionRecord.apkName)
            val apkSavePath = "${applicationDirs.extensionsRoot}/ireader/${extensionRecord.apkName}"

            File("${applicationDirs.extensionsRoot}/ireader").mkdirs()
            downloadAPKFile(apkURL, apkSavePath)
            apkSavePath
        }
    }

    suspend fun installExternalExtension(
        inputStream: InputStream,
        apkName: String,
    ): Int =
        installAPK(true) {
            val rootPath = Path("${applicationDirs.extensionsRoot}/ireader")
            File(rootPath.toString()).mkdirs()
            val downloadedFile = rootPath.resolve(apkName).normalize()

            logger.debug { "Saving IReader apk at $apkName" }
            downloadedFile.outputStream().sink().buffer().use { sink ->
                inputStream.source().use { source ->
                    sink.writeAll(source)
                    sink.flush()
                }
            }
            downloadedFile.absolutePathString()
        }

    suspend fun installAPK(
        forceReinstall: Boolean = false,
        fetcher: suspend () -> String,
    ): Int {
        val apkFilePath = fetcher()
        val paths = ExtensionInstaller.prepareInstallationPaths(apkFilePath, "ireader")

        val isInstalled =
            transaction {
                IReaderExtensionTable.selectAll().where { IReaderExtensionTable.apkName eq paths.apkName }.firstOrNull()
            }?.get(IReaderExtensionTable.isInstalled) ?: false

        val packageInfo = getPackageInfo(apkFilePath)
        val pkgName = packageInfo.packageName

        if (isInstalled && forceReinstall) {
            uninstallExtension(pkgName)
        }

        if (!isInstalled || forceReinstall) {
            // Validate extension feature
            ExtensionInstaller.validateExtensionFeature(packageInfo, EXTENSION_FEATURE, "IReader")

            // Validate lib version
            ExtensionInstaller.validateLibVersion(
                packageInfo.versionName,
                LIB_VERSION_MIN,
                LIB_VERSION_MAX,
                parseAsInt = true,
            )

            // Log metadata for debugging
            ExtensionInstaller.logMetadata(packageInfo, "IReader")

            // Extract metadata
            val isNsfw = ExtensionInstaller.extractNsfwFlag(packageInfo.applicationInfo.metaData, METADATA_NSFW)
            val className = ExtensionInstaller.extractClassName(packageInfo, METADATA_SOURCE_CLASS)

            logger.info { "IReader extension class: $className" }
            logger.debug { "Main class for IReader extension is $className" }

            // Clean up existing JAR if it exists
            ExtensionInstaller.cleanupExistingJar(paths.jarFilePath)

            // Process APK (convert to JAR, extract assets, cleanup)
            ExtensionInstaller.processApk(paths, cleanupApk = true)

            // Load the extension class
            val extensionMainClassInstance =
                try {
                    loadIReaderSource(paths.jarFilePath, className, pkgName)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to load IReader source class: $className" }
                    throw Exception("Could not load IReader source class: ${e.message}")
                }

            // Handle different source types
            // The extension class is loaded from a different classloader, so we need to use reflection
            // to extract source information instead of casting
            fun implementsInterface(
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

            // Helper to extract source info using reflection
            data class SourceInfo(
                val id: Long,
                val name: String,
                val lang: String,
            )

            fun extractSourceInfo(sourceObj: Any): SourceInfo {
                val idMethod = sourceObj.javaClass.getMethod("getId")
                val nameMethod = sourceObj.javaClass.getMethod("getName")
                val langMethod = sourceObj.javaClass.getMethod("getLang")

                return SourceInfo(
                    id = idMethod.invoke(sourceObj) as Long,
                    name = nameMethod.invoke(sourceObj) as String,
                    lang = langMethod.invoke(sourceObj) as String,
                )
            }

            val sourceInfoList: List<SourceInfo> =
                when {
                    implementsInterface(extensionMainClassInstance, "ireader.core.source.SourceFactory") -> {
                        logger.info { "Extension implements SourceFactory" }
                        val method = extensionMainClassInstance.javaClass.getMethod("createSources")
                        val sourcesList = method.invoke(extensionMainClassInstance) as List<*>
                        sourcesList.map { extractSourceInfo(it!!) }
                    }
                    implementsInterface(extensionMainClassInstance, "ireader.core.source.Source") -> {
                        logger.info { "Extension implements Source: ${extensionMainClassInstance.javaClass.name}" }
                        listOf(extractSourceInfo(extensionMainClassInstance))
                    }
                    else -> {
                        val className = extensionMainClassInstance.javaClass.name
                        val superclass = extensionMainClassInstance.javaClass.superclass
                        val interfaces = extensionMainClassInstance.javaClass.interfaces.map { it.name }

                        logger.error {
                            """
                            Invalid IReader extension!
                            Class: $className
                            Superclass: ${superclass?.name}
                            Interfaces: $interfaces
                            
                            Extension must implement ireader.core.source.Source or ireader.core.source.SourceFactory
                            """.trimIndent()
                        }

                        throw RuntimeException("Invalid IReader extension: $className does not implement Source interface")
                    }
                }

            val langs = sourceInfoList.map { it.lang }.toSet()
            val extensionLang =
                when (langs.size) {
                    0 -> ""
                    1 -> langs.first()
                    else -> "all"
                }

            val extensionName =
                packageInfo.applicationInfo.nonLocalizedLabel
                    .toString()
                    .substringAfter("IReader: ")

            transaction {
                if (IReaderExtensionTable.selectAll().where { IReaderExtensionTable.pkgName eq pkgName }.firstOrNull() == null) {
                    IReaderExtensionTable.insert {
                        it[this.apkName] = apkName
                        it[name] = extensionName
                        it[this.pkgName] = packageInfo.packageName
                        it[versionName] = packageInfo.versionName
                        it[versionCode] = packageInfo.versionCode
                        it[lang] = extensionLang
                        it[this.isNsfw] = isNsfw
                    }
                }

                IReaderExtensionTable.update({ IReaderExtensionTable.pkgName eq pkgName }) {
                    it[this.apkName] = apkName
                    it[this.isInstalled] = true
                    it[this.classFQName] = className
                    it[versionName] = packageInfo.versionName
                    it[versionCode] = packageInfo.versionCode
                }

                val extensionId =
                    IReaderExtensionTable
                        .selectAll()
                        .where { IReaderExtensionTable.pkgName eq pkgName }
                        .first()[IReaderExtensionTable.id]
                        .value

                sourceInfoList.forEach { source ->
                    IReaderSourceTable.insert {
                        it[id] = source.id
                        it[name] = source.name
                        it[lang] = source.lang
                        it[extension] = extensionId
                        it[IReaderSourceTable.isNsfw] = isNsfw
                    }
                    logger.info { "Registered IReader source: ${source.name} (${source.lang}) with ID ${source.id}" }
                }
            }
            return 201
        } else {
            return 302
        }
    }

    private val network: NetworkHelper by injectLazy()

    private suspend fun downloadAPKFile(
        url: String,
        savePath: String,
    ) {
        val response =
            network.client
                .newCall(
                    GET(url, cache = CacheControl.FORCE_NETWORK),
                ).await()

        val downloadedFile = File(savePath)
        downloadedFile.sink().buffer().use { sink ->
            response.body.source().use { source ->
                sink.writeAll(source)
                sink.flush()
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        logger.debug { "Uninstalling IReader extension $pkgName" }

        val extensionRecord =
            transaction {
                IReaderExtensionTable.selectAll().where { IReaderExtensionTable.pkgName eq pkgName }.first()
            }
        val fileNameWithoutType = extensionRecord[IReaderExtensionTable.apkName].substringBefore(".apk")
        val jarPath = "${applicationDirs.extensionsRoot}/ireader/$fileNameWithoutType.jar"

        transaction {
            val extensionId = extensionRecord[IReaderExtensionTable.id].value
            IReaderSourceTable.deleteWhere { IReaderSourceTable.extension eq extensionId }

            if (extensionRecord[IReaderExtensionTable.isObsolete]) {
                IReaderExtensionTable.deleteWhere { IReaderExtensionTable.pkgName eq pkgName }
            } else {
                IReaderExtensionTable.update({ IReaderExtensionTable.pkgName eq pkgName }) {
                    it[isInstalled] = false
                }
            }
        }

        if (File(jarPath).exists()) {
            PackageTools.jarLoaderMap.remove(jarPath)?.close()
            File(jarPath).delete()
        }
    }

    suspend fun updateExtension(pkgName: String): Int {
        logger.debug { "Attempting to update extension: $pkgName" }
        logger.debug { "Available updates in updateMap: ${IReaderExtensionsList.updateMap.keys}" }

        val targetExtension =
            IReaderExtensionsList.updateMap.remove(pkgName)
                ?: throw Exception(
                    "No update available for $pkgName. Available updates: ${IReaderExtensionsList.updateMap.keys}. Please refresh the extension list first by calling /api/v1/ireader/extension/list",
                )

        logger.info { "Updating extension $pkgName from version ${targetExtension.versionName}" }
        uninstallExtension(pkgName)
        transaction {
            IReaderExtensionTable.update({ IReaderExtensionTable.pkgName eq pkgName }) {
                it[name] = targetExtension.name
                it[versionName] = targetExtension.versionName
                it[versionCode] = targetExtension.versionCode
                it[lang] = targetExtension.lang
                it[isNsfw] = targetExtension.isNsfw
                it[apkName] = targetExtension.apkName
                it[iconUrl] = targetExtension.iconUrl
                it[hasUpdate] = false
            }
        }
        return installExtension(pkgName)
    }

    suspend fun getExtensionIcon(apkName: String): Pair<InputStream, String> {
        val iconUrl =
            transaction {
                IReaderExtensionTable.selectAll().where { IReaderExtensionTable.apkName eq apkName }.first()
            }[IReaderExtensionTable.iconUrl]

        val cacheSaveDir = "${applicationDirs.extensionsRoot}/ireader/icon"

        return getImageResponse(cacheSaveDir, apkName) {
            network.client
                .newCall(
                    GET(iconUrl, cache = CacheControl.FORCE_NETWORK),
                ).await()
        }
    }

    fun getExtensionIconUrl(apkName: String): String = "/api/v1/ireader/extension/icon/$apkName"

    private fun loadIReaderSource(
        jarPath: String,
        className: String,
        pkgName: String,
    ): Any {
        logger.debug { "Loading IReader source from JAR: $jarPath, class: $className" }

        // Create a ClassLoader that includes both the extension JAR and the server's classpath
        // This allows the extension to access our IReader core API classes
        val classLoader =
            PackageTools.jarLoaderMap[jarPath] ?: URLClassLoader(
                arrayOf(Path(jarPath).toUri().toURL()),
                this::class.java.classLoader, // Use server's classloader as parent
            )
        val classToLoad = Class.forName(className, false, classLoader)

        PackageTools.jarLoaderMap[jarPath] = classLoader

        // Create Dependencies for the source
        val httpClients = ireader.core.http.HttpClients()
        val preferences = ireader.core.prefs.PreferenceStoreImpl(pkgName)
        val dependencies = ireader.core.source.Dependencies(httpClients, preferences)

        // Try to instantiate with Dependencies parameter
        return try {
            // Use reflection to find constructor by parameter count to avoid class loading issues
            val constructors = classToLoad.declaredConstructors
            val dependenciesConstructor = constructors.firstOrNull { it.parameterCount == 1 }

            if (dependenciesConstructor != null) {
                logger.debug { "Found constructor with 1 parameter, using Dependencies" }
                dependenciesConstructor.newInstance(dependencies)
            } else {
                // Try no-arg constructor
                logger.debug { "No single-parameter constructor found, trying no-arg constructor" }
                val noArgConstructor =
                    constructors.firstOrNull { it.parameterCount == 0 }
                        ?: throw Exception("Source class has no compatible constructor: $className")
                noArgConstructor.newInstance()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to instantiate source class: $className" }
            throw Exception("Could not instantiate source: ${e.message}", e)
        }
    }

    private fun findSourceClassInJar(
        jarPath: String,
        packageName: String,
    ): String? {
        try {
            val jarFile = java.util.jar.JarFile(jarPath)
            val entries = jarFile.entries()

            val sourceClasses = mutableListOf<String>()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                // Look for .class files in the package
                if (name.endsWith(".class") && name.startsWith(packageName.replace('.', '/'))) {
                    val className = name.replace('/', '.').removeSuffix(".class")

                    // Skip inner classes and common non-source classes
                    if (!className.contains('$') &&
                        !className.endsWith("BuildConfig") &&
                        !className.endsWith("R")
                    ) {
                        sourceClasses.add(className)
                    }
                }
            }

            jarFile.close()

            logger.debug { "Found ${sourceClasses.size} potential source classes: $sourceClasses" }

            // Prefer classes with common source names
            return sourceClasses.firstOrNull { it.endsWith("SourceFactory") }
                ?: sourceClasses.firstOrNull { it.endsWith("Source") }
                ?: sourceClasses.firstOrNull()
        } catch (e: Exception) {
            logger.error(e) { "Error scanning JAR for source classes" }
            return null
        }
    }
}
