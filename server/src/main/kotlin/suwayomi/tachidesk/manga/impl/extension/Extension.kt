package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.pm.PackageInfo
import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.local.LocalSource
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.bean.Icon
import okhttp3.CacheControl
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.util.AndroidManifestParser
import suwayomi.tachidesk.manga.impl.util.PackageTools
import suwayomi.tachidesk.manga.impl.util.PackageTools.EXTENSION_FEATURE
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MAX
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MIN
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_CONTENT_WARNING
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_EXTENSION_LIB
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_NAME
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_NSFW
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_SOURCE_CLASS
import suwayomi.tachidesk.manga.impl.util.PackageTools.dex2jar
import suwayomi.tachidesk.manga.impl.util.PackageTools.getPackageInfo
import suwayomi.tachidesk.manga.impl.util.PackageTools.loadExtensionSources
import suwayomi.tachidesk.manga.impl.util.ResourceArscIconParser
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.clearCachedImage
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.saveImage
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

object Extension {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()

    private suspend fun fetchExtensionFile(url: String): Path {
        val name = Uri.parse(url).lastPathSegment!!
        val savePath = Path(applicationDirs.tempRoot) / "extensions" / name
        // download jar file
        downloadExtension(url, savePath)

        return savePath
    }

    suspend fun installExtension(pkgName: String): String {
        logger.debug { "Installing $pkgName" }
        val extension =
            transaction {
                ExtensionTable
                    .select(ExtensionTable.apkUrl, ExtensionTable.jarUrl)
                    .where { ExtensionTable.pkgName eq pkgName }
                    .firstOrNull()
            } ?: throw NullPointerException("Could not find extension for $pkgName")
        val jarUrl = extension[ExtensionTable.jarUrl]
        val apkUrl = extension[ExtensionTable.apkUrl]

        return when {
            jarUrl != null -> {
                installExtension {
                    val jar = fetchExtensionFile(jarUrl)
                    val manifest = extractAndParseAndroidManifest(jar)
                    ExtensionPackage.Jar(jar, manifest)
                }
            }

            apkUrl != null -> {
                installExtension {
                    val apk = fetchExtensionFile(apkUrl)
                    val packageInfo = getPackageInfo(apk)
                    ExtensionPackage.Apk(apk, packageInfo)
                }
            }

            else -> {
                throw NullPointerException("Could not find extension url for $pkgName")
            }
        }
    }

    private fun copyToExtensionsRoot(
        inputStream: InputStream,
        extensionName: String,
    ): Path {
        val rootPath = Path(applicationDirs.tempRoot) / "extensions"
        val downloadedFile = rootPath.resolve(extensionName).normalize()
        check(downloadedFile.startsWith(rootPath) && downloadedFile.parent == rootPath) {
            "File '$extensionName' is not a valid extension file"
        }
        logger.debug { "Saving jar at $extensionName" }
        // download jar file
        downloadedFile.createParentDirectories()
        downloadedFile.outputStream().buffered().use { out ->
            inputStream.use {
                it.copyTo(out)
            }
        }
        return downloadedFile
    }

    suspend fun installExternalExtension(
        inputStream: InputStream,
        extensionName: String,
    ): String =
        when {
            extensionName.endsWith(".jar") -> {
                installExtension(
                    true,
                    {
                        val jar = copyToExtensionsRoot(inputStream, extensionName)
                        val manifest = extractAndParseAndroidManifest(jar)
                        ExtensionPackage.Jar(jar, manifest)
                    },
                )
            }

            extensionName.endsWith(".apk") -> {
                installExtension(
                    true,
                    {
                        val apk = copyToExtensionsRoot(inputStream, extensionName)
                        val packageInfo = getPackageInfo(apk)
                        ExtensionPackage.Apk(apk, packageInfo)
                    },
                )
            }

            else -> {
                throw NullPointerException("Could not find extension type for $extensionName")
            }
        }

    sealed class ExtensionPackage {
        abstract val file: Path
        abstract val metadata: PackageMetadata

        // Abstract hook for type-specific preprocessing
        abstract suspend fun prepareJarAndIcons(extensionsRoot: Path): Path

        class Apk(
            override val file: Path,
            val packageInfo: PackageInfo,
        ) : ExtensionPackage() {
            override val metadata =
                PackageMetadata(
                    packageName = packageInfo.packageName,
                    versionName = packageInfo.versionName,
                    versionCode = packageInfo.versionCode,
                    reqFeatures = packageInfo.reqFeatures.orEmpty().map { it.name },
                    metaData = MetadataProvider.FromPackageInfo(packageInfo.applicationInfo.metaData),
                    label = packageInfo.applicationInfo.nonLocalizedLabel?.toString(),
                )

            override suspend fun prepareJarAndIcons(extensionsRoot: Path): Path {
                val jarFile = extensionsRoot / (file.nameWithoutExtension + ".jar")
                dex2jar(file, jarFile)
                extractAssetsFromApk(file, jarFile)
                extractAndCacheApkIcon(file, metadata.packageName)
                file.deleteExisting()
                return jarFile
            }
        }

        class Jar(
            override val file: Path,
            val manifest: AndroidManifestParser.AndroidManifest,
        ) : ExtensionPackage() {
            override val metadata =
                PackageMetadata(
                    packageName = manifest.packageName,
                    versionName = manifest.versionName!!,
                    versionCode = manifest.versionCode!!,
                    reqFeatures = manifest.usesFeatures.mapNotNull { it.name },
                    metaData = MetadataProvider.FromManifest(manifest.application!!.metaData),
                    label = manifest.application.label,
                )

            override suspend fun prepareJarAndIcons(extensionsRoot: Path): Path {
                val jarFile = extensionsRoot / file.name

                ZipFile.builder().setPath(file).get().use { jarZip ->
                    try {
                        cacheIcon(
                            metadata.packageName,
                            ResourceArscIconParser.extractIcon(jarZip),
                        )
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to extract icon from JAR ${metadata.packageName}" }
                    }
                }

                file.copyTo(jarFile)
                file.deleteExisting()
                return jarFile
            }
        }
    }

    data class PackageMetadata(
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val reqFeatures: List<String>,
        val metaData: MetadataProvider,
        val label: String?,
    )

    sealed interface MetadataProvider {
        fun getString(key: String): String?

        fun getInt(key: String): Int

        class FromPackageInfo(
            private val bundle: android.os.Bundle,
        ) : MetadataProvider {
            override fun getString(key: String): String? = bundle.getString(key)

            override fun getInt(key: String): Int = bundle.getInt(key) ?: 0
        }

        class FromManifest(
            private val list: List<AndroidManifestParser.MetaData>,
        ) : MetadataProvider {
            override fun getString(key: String): String? = list.find { it.name == key }?.value

            override fun getInt(key: String): Int = getString(key)?.toIntOrNull() ?: 0
        }
    }

    suspend fun installExtension(
        forceReinstall: Boolean = false,
        fetchPackage: suspend () -> ExtensionPackage,
    ): String {
        val extPackage = fetchPackage()
        val metadata = extPackage.metadata
        val pkgName = metadata.packageName

        val isInstalled =
            transaction {
                ExtensionTable
                    .select(ExtensionTable.isInstalled)
                    .where { ExtensionTable.pkgName eq pkgName }
                    .firstOrNull()
            }?.get(ExtensionTable.isInstalled) ?: false

        if (isInstalled) {
            if (forceReinstall) {
                uninstallExtension(pkgName)
            } else {
                extPackage.file.deleteExisting()
                return pkgName
            }
        }

        if (!metadata.reqFeatures.contains(EXTENSION_FEATURE)) {
            extPackage.file.deleteExisting()
            throw Exception("This file is not a Tachiyomi extension")
        }

        val libVersion = metadata.versionName.substringBeforeLast('.').toDouble()
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            extPackage.file.deleteExisting()
            throw Exception(
                "Lib version is $libVersion, while only versions " +
                    "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed",
            )
        }

        var contentWarning =
            extPackage.metadata
                .metaData
                .getInt(METADATA_CONTENT_WARNING)
        if (contentWarning == 0) {
            contentWarning = extPackage.metadata
                .metaData
                .getString(METADATA_CONTENT_WARNING)
                ?.toIntOrNull() ?: 0
            if (contentWarning == 0) {
                contentWarning = extPackage.metadata
                    .metaData
                    .getString(METADATA_NSFW)
                    ?.toIntOrNull() ?: 0
            }
        }

        val sourceClass =
            metadata.metaData
                .getString(METADATA_SOURCE_CLASS)!!
                .trim()

        val className =
            if (sourceClass.startsWith(".")) {
                pkgName + sourceClass
            } else {
                sourceClass
            }

        logger.debug { "Main class for extension is $className" }

        val extensionsRoot = Path(applicationDirs.extensionsRoot)
        val jarFile = extPackage.prepareJarAndIcons(extensionsRoot)

        try {
            val extensionName =
                metadata.metaData.getString(METADATA_NAME)
                    ?: metadata.label?.substringAfter("Tachiyomi: ")
                    ?: throw Exception("Could not resolve extension name")

            val extensionLibVersion =
                metadata.metaData
                    .getString(METADATA_EXTENSION_LIB)
                    .takeUnless { it == "0" }
                    ?: metadata.versionName.substringBeforeLast('.')

            val apkName =
                when (extPackage) {
                    is ExtensionPackage.Apk -> extPackage.file.name
                    is ExtensionPackage.Jar -> jarFile.name.removeSuffix(".jar") + ".apk"
                }

            setupJar(
                jarFile = jarFile,
                className = className,
                extensionName = extensionName,
                extensionLibVersion = extensionLibVersion,
                apkName = apkName,
                pkgName = pkgName,
                versionName = metadata.versionName,
                versionCode = metadata.versionCode,
                contentWarning = contentWarning,
            )
        } catch (e: Throwable) {
            // free up the file descriptor if exists
            PackageTools.jarLoaderMap.remove(jarFile.absolutePathString())?.close()
            jarFile.deleteIfExists()

            try {
                uninstallExtension(pkgName)
            } catch (_: Throwable) {
            }
            throw e
        }
        return pkgName
    }

    private fun setupJar(
        jarFile: Path,
        className: String,
        extensionName: String,
        extensionLibVersion: String,
        apkName: String,
        pkgName: String,
        versionName: String,
        versionCode: Int,
        contentWarning: Int,
    ) {
        // collect sources from the extension
        val extensionMainClassInstance = loadExtensionSources(jarFile, className)
        val sources: List<Source> =
            when (extensionMainClassInstance) {
                is Source -> listOf(extensionMainClassInstance)
                is SourceFactory -> extensionMainClassInstance.createSources()
                else -> throw RuntimeException("Unknown source class type! ${extensionMainClassInstance.javaClass}")
            }

        val langs = sources.map { it.lang }.toSet()
        val extensionLang =
            when (langs.size) {
                0 -> ""
                1 -> langs.first()
                else -> "all"
            }

        // update extension info
        transaction {
            if (ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.firstOrNull() == null) {
                ExtensionTable.insert {
                    it[this.apkName] = apkName
                    it[name] = extensionName
                    it[this.pkgName] = pkgName
                    it[this.versionName] = versionName
                    it[this.versionCode] = versionCode.toLong()
                    it[extensionLib] = extensionLibVersion
                    it[lang] = extensionLang
                    it[this.contentWarning] = contentWarning
                }
            }

            ExtensionTable.update({ ExtensionTable.pkgName eq pkgName }) {
                it[this.apkName] = apkName
                it[this.isInstalled] = true
                it[this.classFQName] = className
                it[this.versionName] = versionName
                it[this.versionCode] = versionCode.toLong()
            }

            val extensionId =
                ExtensionTable
                    .selectAll()
                    .where { ExtensionTable.pkgName eq pkgName }
                    .first()[ExtensionTable.id]
                    .value

            sources.forEach { httpSource ->
                SourceTable.insert {
                    it[id] = httpSource.id
                    it[name] = httpSource.name
                    it[lang] = httpSource.lang
                    it[extension] = extensionId
                    it[this.contentWarning] = contentWarning
                }
                logger.debug { "Installed source ${httpSource.name} (${httpSource.lang}) with id:${httpSource.id}" }
            }
        }
    }

    private fun extractAndCacheApkIcon(
        apkFile: Path,
        pkgName: String,
    ) {
        try {
            val iconData =
                ApkFile(apkFile.toFile()).use { apk ->
                    apk.allIcons
                        .filterIsInstance<Icon>()
                        .mapNotNull { it.data?.let { data -> data to it.density } }
                        .maxByOrNull { (_, density) -> density }
                        ?.first
                }
            if (iconData == null) {
                logger.warn { "No icon found in APK $pkgName" }
                return
            }
            cacheIcon(pkgName, iconData.inputStream())
        } catch (e: Exception) {
            logger.warn(e) { "Failed to extract icon from APK $pkgName" }
        }
    }

    private fun extractAndCacheJarIcon(
        zipFile: ZipFile,
        pkgName: String,
    ) {
        try {
            val iconStream = ResourceArscIconParser.extractIcon(zipFile)
            cacheIcon(pkgName, iconStream)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to extract icon from JAR $pkgName" }
        }
    }

    private fun cacheIcon(
        pkgName: String,
        inputStream: InputStream,
    ) {
        val iconCacheDir = Path(applicationDirs.extensionsRoot) / "icon"
        iconCacheDir.createDirectories()
        clearCachedImage(iconCacheDir.absolutePathString(), pkgName)
        saveImage("$iconCacheDir/$pkgName", inputStream, null)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun extractAssetsFromApk(
        apkFile: Path,
        jarFile: Path,
    ) {
        val assetsFolder = apkFile.parent / "${apkFile.nameWithoutExtension}_assets"
        assetsFolder.createDirectories()
        ZipInputStream(apkFile.inputStream()).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name.startsWith("assets/") && !zipEntry.isDirectory) {
                    val assetFile = assetsFolder / zipEntry.name
                    assetFile.createParentDirectories()
                    assetFile.outputStream().use { outputStream ->
                        zipInputStream.copyTo(outputStream)
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
        }

        val tempJarFile = jarFile.parent / "${jarFile.nameWithoutExtension}_temp.jar"
        ZipInputStream(jarFile.inputStream()).use { jarZipInputStream ->
            ZipOutputStream(tempJarFile.outputStream()).use { jarZipOutputStream ->
                var zipEntry = jarZipInputStream.nextEntry
                while (zipEntry != null) {
                    if (!zipEntry.name.startsWith("META-INF/")) {
                        jarZipOutputStream.putNextEntry(ZipEntry(zipEntry.name))
                        jarZipInputStream.copyTo(jarZipOutputStream)
                    }
                    zipEntry = jarZipInputStream.nextEntry
                }
                assetsFolder.walk().forEach { file ->
                    if (file.isRegularFile()) {
                        jarZipOutputStream.putNextEntry(ZipEntry(file.relativeTo(assetsFolder).toString().replace("\\", "/")))
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(jarZipOutputStream)
                        }
                        jarZipOutputStream.closeEntry()
                    }
                }
            }
        }

        jarFile.deleteIfExists()
        tempJarFile.copyTo(jarFile)
        tempJarFile.deleteExisting()

        assetsFolder.deleteRecursively()
    }

    private fun extractAndParseAndroidManifest(jar: Path): AndroidManifestParser.AndroidManifest =
        ZipFile.builder().setPath(jar).get().use { jarZip ->
            jarZip.getInputStream(jarZip.getEntry("AndroidManifest.xml")).use {
                AndroidManifestParser.parse(it)
            }
        }

    private val network: NetworkHelper by injectLazy()

    private suspend fun downloadExtension(
        url: String,
        savePath: Path,
    ) {
        val response =
            network.client
                .newCall(
                    GET(url, cache = CacheControl.FORCE_NETWORK),
                ).await()

        savePath.createParentDirectories()
        response.body.byteStream().use {
            savePath.outputStream().buffered().use { out ->
                it.copyTo(out)
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        logger.debug { "Uninstalling $pkgName" }

        val extensionRecord = transaction { ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.first() }
        val fileNameWithoutType =
            extensionRecord[ExtensionTable.apkName]?.substringBefore(".apk")
                ?: throw NullPointerException("Missing $pkgName apkName")
        val jarPath = Path(applicationDirs.extensionsRoot) / "$fileNameWithoutType.jar"
        val sources =
            transaction {
                val extensionId = extensionRecord[ExtensionTable.id].value

                val sources = SourceTable.selectAll().where { SourceTable.extension eq extensionId }.map { it[SourceTable.id].value }

                SourceTable.deleteWhere { SourceTable.extension eq extensionId }

                if (extensionRecord[ExtensionTable.isObsolete] || extensionRecord[ExtensionTable.apkUrl] == null) {
                    ExtensionTable.deleteWhere { ExtensionTable.pkgName eq pkgName }
                } else {
                    ExtensionTable.update({ ExtensionTable.pkgName eq pkgName }) {
                        it[isInstalled] = false
                        it[hasUpdate] = false
                        it[apkName] = null
                    }
                }

                sources
            }

        if (jarPath.exists()) {
            // free up the file descriptor if exists
            PackageTools.jarLoaderMap.remove(jarPath.absolutePathString())?.close()

            // clear all loaded sources
            sources.forEach { GetSource.unregisterSource(it) }

            jarPath.deleteExisting()
        }
    }

    suspend fun updateExtension(pkgName: String): String {
        val targetExtension = ExtensionsList.updateMap.remove(pkgName)!!
        uninstallExtension(pkgName)
        transaction {
            ExtensionTable.update({ ExtensionTable.pkgName eq pkgName }) {
                it[name] = targetExtension.name
                it[versionName] = targetExtension.versionName
                it[versionCode] = targetExtension.versionCode
                it[lang] = targetExtension.lang
                it[contentWarning] = targetExtension.contentWarning.ordinal
                it[iconUrl] = targetExtension.iconUrl
                it[hasUpdate] = false
            }
        }
        return installExtension(pkgName)
    }

    suspend fun getExtensionIcon(pkgName: String): Pair<InputStream, String> {
        val cacheSaveDir = "${applicationDirs.extensionsRoot}/icon"

        if (pkgName == LocalSource::class.java.`package`.name) {
            return getImageResponse(cacheSaveDir, "localSource") {
                network.client
                    .newCall(GET("", cache = CacheControl.FORCE_NETWORK))
                    .await()
            }
        }

        val iconUrl =
            transaction { ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.first() }[ExtensionTable.iconUrl]

        return getImageResponse(cacheSaveDir, pkgName) {
            network.client
                .newCall(
                    GET(iconUrl, cache = CacheControl.FORCE_NETWORK),
                ).await()
        }
    }

    fun proxyExtensionIconUrl(pkgName: String): String = "/api/v1/extension/icon/$pkgName"
}
