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
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtensionsList.extensionTableAsDataClass
import suwayomi.tachidesk.manga.impl.util.PackageTools
import suwayomi.tachidesk.manga.impl.util.PackageTools.dex2jar
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
        val apkName = File(apkFilePath).name

        val isInstalled =
            transaction {
                IReaderExtensionTable.selectAll().where { IReaderExtensionTable.apkName eq apkName }.firstOrNull()
            }?.get(IReaderExtensionTable.isInstalled) ?: false

        val fileNameWithoutType = apkName.substringBefore(".apk")
        val dirPathWithoutType = "${applicationDirs.extensionsRoot}/ireader/$fileNameWithoutType"
        val jarFilePath = "$dirPathWithoutType.jar"
        val dexFilePath = "$dirPathWithoutType.dex"

        val packageInfo = getPackageInfo(apkFilePath)
        val pkgName = packageInfo.packageName
        
        if (isInstalled && forceReinstall) {
            uninstallExtension(pkgName)
        }

        if (!isInstalled || forceReinstall) {
            // Check for IReader extension feature
            val hasIReaderFeature = packageInfo.reqFeatures.orEmpty().any { 
                it.name == EXTENSION_FEATURE || it.name.contains("ireader", ignoreCase = true)
            }
            
            // Log available features for debugging
            logger.debug { "Package features: ${packageInfo.reqFeatures?.map { it.name }}" }
            logger.debug { "Package metadata: ${packageInfo.applicationInfo.metaData}" }
            
            if (!hasIReaderFeature) {
                logger.warn { "APK does not have IReader extension feature. Available features: ${packageInfo.reqFeatures?.map { it.name }}" }
                // For now, continue anyway to see what happens
                // throw Exception("This apk is not an IReader extension")
            }

            val libVersion = packageInfo.versionName.substringBefore('.').toInt()
            if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
                throw Exception(
                    "Lib version is $libVersion, while only versions $LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed"
                )
            }

            // Log all metadata for debugging
            val metaData = packageInfo.applicationInfo.metaData
            if (metaData != null) {
                logger.debug { "APK Metadata keys: ${metaData.keySet()}" }
                metaData.keySet().forEach { key ->
                    val value = try {
                        when {
                            key.contains("nsfw", ignoreCase = true) -> {
                                // Try Int first, fallback to String
                                try {
                                    metaData.getInt(key, -1)
                                } catch (e: ClassCastException) {
                                    metaData.getString(key)
                                }
                            }
                            else -> metaData.getString(key)
                        }
                    } catch (e: Exception) {
                        "error: ${e.message}"
                    }
                    logger.debug { "  $key = $value" }
                }
            } else {
                logger.warn { "No metadata found in APK" }
            }
            
            // NSFW can be stored as either String or Int in metadata
            val isNsfw = try {
                metaData?.getInt(METADATA_NSFW, 0) == 1
            } catch (e: ClassCastException) {
                metaData?.getString(METADATA_NSFW) == "1"
            }
            val sourceClassName = metaData?.getString(METADATA_SOURCE_CLASS)?.trim()
            
            if (sourceClassName == null) {
                throw Exception("Failed to load extension, the package ${pkgName} didn't define source class")
            }
            
            // Handle relative class names (starting with .)
            val className = if (sourceClassName.startsWith(".")) {
                packageInfo.packageName + sourceClassName
            } else {
                sourceClassName
            }
            
            logger.info { "IReader extension class: $className" }

            logger.debug { "Main class for IReader extension is $className" }

            // Delete existing JAR file if it exists to avoid file locking issues
            val jarFile = File(jarFilePath)
            if (jarFile.exists()) {
                logger.debug { "Deleting existing JAR file: $jarFilePath" }
                
                // First, try to close any existing ClassLoader for this JAR
                PackageTools.jarLoaderMap.remove(jarFilePath)?.close()
                
                var deleted = false
                for (i in 1..5) {
                    try {
                        deleted = jarFile.delete()
                        if (deleted) break
                        Thread.sleep(100)
                    } catch (e: Exception) {
                        logger.warn { "Attempt $i: Could not delete existing JAR: ${e.message}" }
                        Thread.sleep(100)
                    }
                }
                if (!deleted) {
                    throw Exception("Could not delete existing JAR file at $jarFilePath - it may be locked by another process. Try restarting the server.")
                }
            }

            dex2jar(apkFilePath, jarFilePath, fileNameWithoutType)
            
            // Give Windows time to release file locks
            Thread.sleep(100)
            
            extractAssetsFromApk(apkFilePath, jarFilePath)

            // Clean up temporary files
            try {
                File(apkFilePath).delete()
            } catch (e: Exception) {
                logger.warn { "Could not delete APK file: ${e.message}" }
            }
            
            try {
                File(dexFilePath).delete()
            } catch (e: Exception) {
                logger.warn { "Could not delete DEX file: ${e.message}" }
            }

            // Load the extension class
            val extensionMainClassInstance = try {
                loadIReaderSource(jarFilePath, className, pkgName)
            } catch (e: Exception) {
                logger.error(e) { "Failed to load IReader source class: $className" }
                throw Exception("Could not load IReader source class: ${e.message}")
            }
            // Handle different source types
            // The extension class is loaded from a different classloader, so we need to use reflection
            // to extract source information instead of casting
            fun implementsInterface(obj: Any, interfaceName: String): Boolean {
                fun checkClass(clazz: Class<*>?): Boolean {
                    if (clazz == null) return false
                    if (clazz.interfaces.any { it.name == interfaceName }) return true
                    if (checkClass(clazz.superclass)) return true
                    return clazz.interfaces.any { checkClass(it) }
                }
                return checkClass(obj.javaClass)
            }
            
            // Helper to extract source info using reflection
            data class SourceInfo(val id: Long, val name: String, val lang: String)
            
            fun extractSourceInfo(sourceObj: Any): SourceInfo {
                val idMethod = sourceObj.javaClass.getMethod("getId")
                val nameMethod = sourceObj.javaClass.getMethod("getName")
                val langMethod = sourceObj.javaClass.getMethod("getLang")
                
                return SourceInfo(
                    id = idMethod.invoke(sourceObj) as Long,
                    name = nameMethod.invoke(sourceObj) as String,
                    lang = langMethod.invoke(sourceObj) as String
                )
            }
            
            val sourceInfoList: List<SourceInfo> = when {
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

    private fun extractAssetsFromApk(
        apkPath: String,
        jarPath: String,
    ) {
        val apkFile = File(apkPath)
        val jarFile = File(jarPath)

        val assetsFolder = File("${apkFile.parent}/${apkFile.nameWithoutExtension}_assets")
        assetsFolder.mkdir()
        ZipInputStream(apkFile.inputStream()).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name.startsWith("assets/") && !zipEntry.isDirectory) {
                    val assetFile = File(assetsFolder, zipEntry.name)
                    assetFile.parentFile.mkdirs()
                    FileOutputStream(assetFile).use { outputStream ->
                        zipInputStream.copyTo(outputStream)
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
        }

        val tempJarFile = File("${jarFile.parent}/${jarFile.nameWithoutExtension}_temp.jar")
        ZipInputStream(jarFile.inputStream()).use { jarZipInputStream ->
            ZipOutputStream(FileOutputStream(tempJarFile)).use { jarZipOutputStream ->
                var zipEntry = jarZipInputStream.nextEntry
                while (zipEntry != null) {
                    if (!zipEntry.name.startsWith("META-INF/")) {
                        jarZipOutputStream.putNextEntry(ZipEntry(zipEntry.name))
                        jarZipInputStream.copyTo(jarZipOutputStream)
                    }
                    zipEntry = jarZipInputStream.nextEntry
                }
                assetsFolder.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        jarZipOutputStream.putNextEntry(ZipEntry(file.relativeTo(assetsFolder).toString().replace("\\", "/")))
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(jarZipOutputStream)
                        }
                        jarZipOutputStream.closeEntry()
                    }
                }
            }
        }

        // Retry logic for Windows file locking issues
        var retries = 5
        var deleted = false
        while (retries > 0 && !deleted) {
            try {
                deleted = jarFile.delete()
                if (!deleted) {
                    Thread.sleep(100)
                    retries--
                }
            } catch (e: Exception) {
                logger.warn { "Retry ${6 - retries}: Could not delete JAR file: ${e.message}" }
                Thread.sleep(100)
                retries--
            }
        }
        
        if (!deleted) {
            logger.error { "Failed to delete original JAR file after multiple retries" }
            throw Exception("Could not replace JAR file - it may be locked by another process")
        }
        
        if (!tempJarFile.renameTo(jarFile)) {
            logger.error { "Failed to rename temp JAR file" }
            throw Exception("Could not rename temporary JAR file")
        }
        
        assetsFolder.deleteRecursively()
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

        val extensionRecord = transaction { 
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
        val targetExtension = IReaderExtensionsList.updateMap.remove(pkgName)
            ?: throw Exception("No update available for $pkgName. Please refresh the extension list first by calling /api/v1/ireader/extension/list")
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
    
    private fun loadIReaderSource(jarPath: String, className: String, pkgName: String): Any {
        logger.debug { "Loading IReader source from JAR: $jarPath, class: $className" }
        
        // Create a ClassLoader that includes both the extension JAR and the server's classpath
        // This allows the extension to access our IReader core API classes
        val classLoader = PackageTools.jarLoaderMap[jarPath] ?: URLClassLoader(
            arrayOf(Path(jarPath).toUri().toURL()),
            this::class.java.classLoader // Use server's classloader as parent
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
                val noArgConstructor = constructors.firstOrNull { it.parameterCount == 0 }
                    ?: throw Exception("Source class has no compatible constructor: $className")
                noArgConstructor.newInstance()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to instantiate source class: $className" }
            throw Exception("Could not instantiate source: ${e.message}", e)
        }
    }
    
    private fun findSourceClassInJar(jarPath: String, packageName: String): String? {
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
                        !className.endsWith("R")) {
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
