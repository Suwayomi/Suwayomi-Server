package suwayomi.tachidesk.manga.impl.extension.ireader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import ireader.core.prefs.PreferenceStoreFactory
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
import suwayomi.tachidesk.manga.impl.util.BytecodeEditor
import suwayomi.tachidesk.manga.impl.util.PackageTools
import suwayomi.tachidesk.manga.impl.util.PackageTools.getPackageInfo
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.net.URLClassLoader
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream

/**
 * Handles IReader extension installation, uninstallation, and updates.
 * 
 * This implementation follows the same patterns as the Tachiyomi Extension handler,
 * using shared utilities from ExtensionInstaller where possible.
 */
object IReaderExtension {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val network: NetworkHelper by injectLazy()

    // IReader-specific constants
    private const val EXTENSION_FEATURE = "ireader"
    private const val METADATA_SOURCE_CLASS = "source.class"
    private const val METADATA_NSFW = "source.nsfw"
    private const val LIB_VERSION_MIN = 2
    private const val LIB_VERSION_MAX = 2
    private const val IREADER_SUBDIR = "ireader"

    /**
     * Install an IReader extension by package name from the repository.
     */
    suspend fun installExtension(pkgName: String): Int {
        logger.debug { "Installing IReader extension $pkgName" }
        val extensionRecord = extensionTableAsDataClass().first { it.pkgName == pkgName }

        return installJAR(pkgName) {
            val repo = extensionRecord.repo ?: throw NullPointerException("Could not find extension repo")
            val jarURL = IReaderGithubApi.getJarUrl(repo, extensionRecord.apkName)
            val jarName = extensionRecord.apkName.replace(".apk", ".jar")
            val jarSavePath = "${applicationDirs.extensionsRoot}/$IREADER_SUBDIR/$jarName"

            File("${applicationDirs.extensionsRoot}/$IREADER_SUBDIR").mkdirs()
            downloadFile(jarURL, jarSavePath)
            jarSavePath
        }
    }

    /**
     * Install an external IReader extension from an input stream.
     */
    suspend fun installExternalExtension(
        inputStream: InputStream,
        fileName: String,
    ): Int {
        val rootPath = Path("${applicationDirs.extensionsRoot}/$IREADER_SUBDIR")
        File(rootPath.toString()).mkdirs()

        val isJar = fileName.endsWith(".jar")
        val downloadedFile = rootPath.resolve(fileName).normalize()

        logger.debug { "Saving IReader extension at $fileName" }
        downloadedFile.outputStream().sink().buffer().use { sink ->
            inputStream.source().use { source ->
                sink.writeAll(source)
                sink.flush()
            }
        }

        return if (isJar) {
            installExternalJAR(downloadedFile.absolutePathString())
        } else {
            @Suppress("DEPRECATION")
            installAPK(true) { downloadedFile.absolutePathString() }
        }
    }

    /**
     * Install a JAR file directly (preferred method for IReader extensions).
     * JAR files are pre-built by the IReader extensions repo.
     */
    suspend fun installJAR(
        pkgName: String,
        forceReinstall: Boolean = false,
        fetcher: suspend () -> String,
    ): Int {
        val jarFilePath = fetcher()
        val jarFile = File(jarFilePath)
        val jarName = jarFile.name

        logger.info { "Installing IReader extension from JAR: $jarName" }

        val extensionRecord = extensionTableAsDataClass().first { it.pkgName == pkgName }

        val isInstalled =
            transaction {
                IReaderExtensionTable.selectAll()
                    .where { IReaderExtensionTable.pkgName eq pkgName }
                    .firstOrNull()
            }?.get(IReaderExtensionTable.isInstalled) ?: false

        if (isInstalled && forceReinstall) {
            uninstallExtension(pkgName)
        }

        if (!isInstalled || forceReinstall) {
            if (jarFile.exists()) {
                logger.debug { "JAR file already exists: $jarFilePath" }
                PackageTools.jarLoaderMap.remove(jarFilePath)?.close()
            }

            val className = findSourceClassInJar(jarFilePath, pkgName)
                ?: throw Exception("Could not find source class in JAR for package $pkgName")

            logger.info { "Found IReader source class: $className" }

            val extensionMainClassInstance = loadIReaderSource(jarFilePath, className, pkgName)
            val sourceInfoList = extractSourceInfoFromInstance(extensionMainClassInstance)

            val extensionLang = determineExtensionLang(sourceInfoList)

            transaction {
                IReaderExtensionTable.update({ IReaderExtensionTable.pkgName eq pkgName }) {
                    it[this.isInstalled] = true
                    it[this.classFQName] = className
                }

                val extensionId = getExtensionId(pkgName)

                sourceInfoList.forEach { source ->
                    IReaderSourceTable.insert {
                        it[id] = source.id
                        it[name] = source.name
                        it[lang] = source.lang
                        it[extension] = extensionId
                        it[IReaderSourceTable.isNsfw] = extensionRecord.isNsfw
                    }
                    logger.debug { "Registered IReader source: ${source.name} (${source.lang}) with id:${source.id}" }
                }
            }
            return 201
        } else {
            return 302
        }
    }

    /**
     * Install an external JAR file (uploaded by user).
     */
    private suspend fun installExternalJAR(jarFilePath: String): Int {
        val jarFile = File(jarFilePath)
        val jarName = jarFile.name

        logger.info { "Installing external IReader JAR: $jarName" }

        val pkgName = findPackageNameInJar(jarFilePath)
            ?: throw Exception("Could not determine package name from JAR")

        val className = findSourceClassInJar(jarFilePath, pkgName)
            ?: throw Exception("Could not find source class in JAR")

        logger.info { "Found package: $pkgName, class: $className" }

        PackageTools.jarLoaderMap.remove(jarFilePath)?.close()

        val extensionMainClassInstance = loadIReaderSource(jarFilePath, className, pkgName)
        val sourceInfoList = extractSourceInfoFromInstance(extensionMainClassInstance)

        val extensionLang = determineExtensionLang(sourceInfoList)
        val extensionName = sourceInfoList.firstOrNull()?.name ?: jarName.removeSuffix(".jar")

        transaction {
            val existingExtension = IReaderExtensionTable.selectAll()
                .where { IReaderExtensionTable.pkgName eq pkgName }
                .firstOrNull()

            if (existingExtension == null) {
                IReaderExtensionTable.insert {
                    it[this.apkName] = jarName.replace(".jar", ".apk")
                    it[name] = extensionName
                    it[this.pkgName] = pkgName
                    it[versionName] = "1.0"
                    it[versionCode] = 1
                    it[lang] = extensionLang
                    it[isNsfw] = false
                    it[isInstalled] = true
                    it[classFQName] = className
                }
            } else {
                IReaderExtensionTable.update({ IReaderExtensionTable.pkgName eq pkgName }) {
                    it[isInstalled] = true
                    it[classFQName] = className
                }
            }

            val extensionId = getExtensionId(pkgName)

            IReaderSourceTable.deleteWhere { IReaderSourceTable.extension eq extensionId }

            sourceInfoList.forEach { source ->
                IReaderSourceTable.insert {
                    it[id] = source.id
                    it[name] = source.name
                    it[lang] = source.lang
                    it[extension] = extensionId
                    it[IReaderSourceTable.isNsfw] = false
                }
                logger.debug { "Registered IReader source: ${source.name} (${source.lang}) with id:${source.id}" }
            }
        }
        return 201
    }

    /**
     * Install from APK (legacy method - requires dex2jar conversion).
     */
    @Deprecated("Use installJAR instead - APK installation requires dex2jar conversion")
    suspend fun installAPK(
        forceReinstall: Boolean = false,
        fetcher: suspend () -> String,
    ): Int {
        val apkFilePath = fetcher()
        val paths = ExtensionInstaller.prepareInstallationPaths(apkFilePath, IREADER_SUBDIR)

        val isInstalled =
            transaction {
                IReaderExtensionTable.selectAll()
                    .where { IReaderExtensionTable.apkName eq paths.apkName }
                    .firstOrNull()
            }?.get(IReaderExtensionTable.isInstalled) ?: false

        val packageInfo = getPackageInfo(apkFilePath)
        val pkgName = packageInfo.packageName

        if (isInstalled && forceReinstall) {
            uninstallExtension(pkgName)
        }

        if (!isInstalled || forceReinstall) {
            // Validate extension
            ExtensionInstaller.validateExtensionFeature(packageInfo, EXTENSION_FEATURE, "IReader")
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

            logger.debug { "Main class for IReader extension is $className" }

            // Clean up existing JAR
            ExtensionInstaller.cleanupExistingJar(paths.jarFilePath)

            // Process APK (convert to JAR, extract assets)
            ExtensionInstaller.processApk(paths, cleanupApk = false)

            // Fix stackmap frames (IReader-specific)
            logger.info { "Fixing stackmap frames in JAR: ${paths.jarFilePath}" }
            try {
                BytecodeEditor.fixStackmapFrames(Path(paths.jarFilePath))
            } catch (e: Exception) {
                logger.error(e) { "Failed to fix stackmap frames" }
            }

            // Clean up APK and DEX files
            cleanupTempFiles(paths.apkFilePath, paths.dexFilePath)

            // Load the extension
            val extensionMainClassInstance = loadIReaderSource(paths.jarFilePath, className, pkgName)
            val sourceInfoList = extractSourceInfoFromInstance(extensionMainClassInstance)

            val extensionLang = determineExtensionLang(sourceInfoList)
            val extensionName = packageInfo.applicationInfo.nonLocalizedLabel
                .toString()
                .substringAfter("IReader: ")

            // Update database
            transaction {
                if (IReaderExtensionTable.selectAll()
                        .where { IReaderExtensionTable.pkgName eq pkgName }
                        .firstOrNull() == null
                ) {
                    IReaderExtensionTable.insert {
                        it[this.apkName] = paths.apkName
                        it[name] = extensionName
                        it[this.pkgName] = packageInfo.packageName
                        it[versionName] = packageInfo.versionName
                        it[versionCode] = packageInfo.versionCode
                        it[lang] = extensionLang
                        it[this.isNsfw] = isNsfw
                    }
                }

                IReaderExtensionTable.update({ IReaderExtensionTable.pkgName eq pkgName }) {
                    it[this.apkName] = paths.apkName
                    it[this.isInstalled] = true
                    it[this.classFQName] = className
                    it[versionName] = packageInfo.versionName
                    it[versionCode] = packageInfo.versionCode
                }

                val extensionId = getExtensionId(pkgName)

                sourceInfoList.forEach { source ->
                    IReaderSourceTable.insert {
                        it[id] = source.id
                        it[name] = source.name
                        it[lang] = source.lang
                        it[extension] = extensionId
                        it[IReaderSourceTable.isNsfw] = isNsfw
                    }
                    logger.debug { "Registered IReader source: ${source.name} (${source.lang}) with id:${source.id}" }
                }
            }
            return 201
        } else {
            return 302
        }
    }

    /**
     * Uninstall an IReader extension.
     */
    fun uninstallExtension(pkgName: String) {
        logger.debug { "Uninstalling IReader extension $pkgName" }

        val extensionRecord = transaction {
            IReaderExtensionTable.selectAll()
                .where { IReaderExtensionTable.pkgName eq pkgName }
                .first()
        }

        val fileNameWithoutType = extensionRecord[IReaderExtensionTable.apkName].substringBefore(".apk")
        val jarPath = "${applicationDirs.extensionsRoot}/$IREADER_SUBDIR/$fileNameWithoutType.jar"

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

    /**
     * Update an IReader extension to the latest version.
     */
    suspend fun updateExtension(pkgName: String): Int {
        logger.debug { "Attempting to update extension: $pkgName" }

        val targetExtension = IReaderExtensionsList.updateMap.remove(pkgName)
            ?: throw Exception(
                "No update available for $pkgName. " +
                    "Available updates: ${IReaderExtensionsList.updateMap.keys}. " +
                    "Please refresh the extension list first.",
            )

        logger.info { "Updating extension $pkgName to version ${targetExtension.versionName}" }
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

    /**
     * Get the icon for an IReader extension.
     */
    suspend fun getExtensionIcon(apkName: String): Pair<InputStream, String> {
        val iconUrl = transaction {
            IReaderExtensionTable.selectAll()
                .where { IReaderExtensionTable.apkName eq apkName }
                .first()
        }[IReaderExtensionTable.iconUrl]

        val cacheSaveDir = "${applicationDirs.extensionsRoot}/$IREADER_SUBDIR/icon"

        return getImageResponse(cacheSaveDir, apkName) {
            network.client
                .newCall(GET(iconUrl, cache = CacheControl.FORCE_NETWORK))
                .await()
        }
    }

    /**
     * Get the URL for an extension icon.
     */
    fun getExtensionIconUrl(apkName: String): String = "/api/v1/ireader/extension/icon/$apkName"

    // ==================== Private Helper Methods ====================

    /**
     * Helper data class for source info extraction.
     */
    private data class SourceInfo(
        val id: Long,
        val name: String,
        val lang: String,
    )

    /**
     * Determine extension language from source list.
     */
    private fun determineExtensionLang(sourceInfoList: List<SourceInfo>): String {
        val langs = sourceInfoList.map { it.lang }.toSet()
        return when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }
    }

    /**
     * Get extension ID from database.
     */
    private fun getExtensionId(pkgName: String): Int =
        IReaderExtensionTable
            .selectAll()
            .where { IReaderExtensionTable.pkgName eq pkgName }
            .first()[IReaderExtensionTable.id]
            .value

    /**
     * Download a file from URL to local path.
     */
    private suspend fun downloadFile(url: String, savePath: String) {
        logger.debug { "Downloading file from $url to $savePath" }
        val response = network.client
            .newCall(GET(url, cache = CacheControl.FORCE_NETWORK))
            .await()

        val downloadedFile = File(savePath)
        downloadedFile.sink().buffer().use { sink ->
            response.body.source().use { source ->
                sink.writeAll(source)
                sink.flush()
            }
        }
        logger.debug { "Downloaded ${downloadedFile.length()} bytes" }
    }

    /**
     * Clean up temporary APK and DEX files.
     */
    private fun cleanupTempFiles(apkPath: String, dexPath: String) {
        try {
            File(apkPath).delete()
        } catch (e: Exception) {
            logger.warn { "Could not delete APK file: ${e.message}" }
        }

        try {
            File(dexPath).delete()
        } catch (e: Exception) {
            logger.warn { "Could not delete DEX file: ${e.message}" }
        }
    }

    /**
     * Load an IReader source from a JAR file.
     */
    private fun loadIReaderSource(jarPath: String, className: String, pkgName: String): Any {
        logger.debug { "Loading IReader source from JAR: $jarPath, class: $className" }

        val systemClassLoader = URLClassLoader.getSystemClassLoader()

        val classLoader = PackageTools.jarLoaderMap[jarPath] ?: URLClassLoader(
            arrayOf(File(jarPath).toURI().toURL()),
            systemClassLoader,
        )

        PackageTools.jarLoaderMap[jarPath] = classLoader

        val preferences = PreferenceStoreFactory().create(pkgName)
        val httpClients = ireader.core.http.HttpClients(preferences)
        val dependencies = ireader.core.source.Dependencies(httpClients, preferences)

        return try {
            val obj = Class.forName(className, false, classLoader)
                .getConstructor(ireader.core.source.Dependencies::class.java)
                .newInstance(dependencies)

            logger.info { "Successfully loaded IReader source: $className" }
            obj
        } catch (e: Throwable) {
            logger.error(e) { "Failed to load catalog $pkgName" }
            throw Exception("Could not instantiate source: ${e.message}", e)
        }
    }

    /**
     * Extract source info from an extension instance using reflection.
     */
    private fun extractSourceInfoFromInstance(extensionMainClassInstance: Any): List<SourceInfo> {
        fun implementsInterface(obj: Any, interfaceName: String): Boolean {
            fun checkClass(clazz: Class<*>?): Boolean {
                if (clazz == null) return false
                if (clazz.interfaces.any { it.name == interfaceName }) return true
                if (checkClass(clazz.superclass)) return true
                return clazz.interfaces.any { checkClass(it) }
            }
            return checkClass(obj.javaClass)
        }

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

        return when {
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
    }

    /**
     * Find package name from classes in JAR.
     */
    private fun findPackageNameInJar(jarPath: String): String? {
        try {
            val jarFile = java.util.jar.JarFile(jarPath)
            val entries = jarFile.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                if (name.endsWith(".class") && !name.contains('$')) {
                    val className = name.replace('/', '.').removeSuffix(".class")
                    val lastDot = className.lastIndexOf('.')
                    if (lastDot > 0) {
                        jarFile.close()
                        return className.substring(0, lastDot)
                    }
                }
            }
            jarFile.close()
        } catch (e: Exception) {
            logger.error(e) { "Error finding package name in JAR" }
        }
        return null
    }

    /**
     * Find the source class in a JAR file.
     * 
     * IReader extensions use KSP to generate a class called `tachiyomix.extension.Extension`
     * which is the entry point for the extension.
     */
    private fun findSourceClassInJar(jarPath: String, packageName: String): String? {
        val standardExtensionClass = "tachiyomix.extension.Extension"

        try {
            val jarFile = java.util.jar.JarFile(jarPath)
            val entries = jarFile.entries()

            val allClasses = mutableListOf<String>()
            var hasStandardExtension = false

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                if (name.endsWith(".class") && !name.contains('$')) {
                    val className = name.replace('/', '.').removeSuffix(".class")
                    allClasses.add(className)

                    if (className == standardExtensionClass) {
                        hasStandardExtension = true
                    }
                }
            }

            jarFile.close()

            logger.debug { "JAR contains ${allClasses.size} classes" }

            if (hasStandardExtension) {
                logger.info { "Found standard IReader extension class: $standardExtensionClass" }
                return standardExtensionClass
            }

            logger.warn { "Standard extension class not found, searching for alternatives..." }

            val sourceClasses = allClasses.filter { className ->
                !className.endsWith("BuildConfig") &&
                    !className.endsWith("R") &&
                    !className.endsWith("Kt") &&
                    !className.contains(".R$") &&
                    (className.endsWith("Source") ||
                        className.endsWith("SourceFactory") ||
                        className.endsWith("Extension") ||
                        className.contains("Source"))
            }

            logger.debug { "Potential source classes: $sourceClasses" }

            return sourceClasses.firstOrNull { it.endsWith("Extension") }
                ?: sourceClasses.firstOrNull { it.endsWith("SourceFactory") }
                ?: sourceClasses.firstOrNull { it.endsWith("Source") }
                ?: sourceClasses.firstOrNull()
                ?: allClasses.firstOrNull {
                    !it.endsWith("BuildConfig") &&
                        !it.endsWith("R") &&
                        !it.endsWith("Kt")
                }
        } catch (e: Exception) {
            logger.error(e) { "Error scanning JAR for source classes" }
            return null
        }
    }
}
