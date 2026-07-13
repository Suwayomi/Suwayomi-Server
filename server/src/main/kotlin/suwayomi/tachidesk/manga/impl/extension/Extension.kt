package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

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
import okio.buffer
import okio.sink
import okio.source
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
import kotlin.collections.any
import kotlin.collections.orEmpty
import kotlin.io.inputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
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
                installJAR {
                    val jarName = Uri.parse(jarUrl).lastPathSegment!!
                    val jarSavePath = "${applicationDirs.extensionsRoot}/$jarName"
                    // download jar file
                    downloadExtension(jarUrl, jarSavePath)

                    jarSavePath
                }
            }
            apkUrl != null -> {
                installAPK {
                    val apkName = Uri.parse(apkUrl).lastPathSegment!!
                    val apkSavePath = "${applicationDirs.extensionsRoot}/$apkName"
                    // download apk file
                    downloadExtension(apkUrl, apkSavePath)

                    apkSavePath
                }
            }
            else -> throw NullPointerException("Could not find extension url for $pkgName")
        }
    }

    suspend fun installExternalExtension(
        inputStream: InputStream,
        extensionName: String,
    ): String {
        val copyToExtensionsRoot = {
            val rootPath = Path(applicationDirs.extensionsRoot)
            val downloadedFile = rootPath.resolve(extensionName).normalize()
            check(downloadedFile.startsWith(rootPath) && downloadedFile.parent == rootPath) {
                "File '$extensionName' is not a valid extension file"
            }
            logger.debug { "Saving jar at $extensionName" }
            // download jar file
            downloadedFile.outputStream().sink().buffer().use { sink ->
                inputStream.source().use { source ->
                    sink.writeAll(source)
                    sink.flush()
                }
            }
            downloadedFile.absolutePathString()
        }

        return when {
            extensionName.endsWith(".jar") -> installJAR(true, copyToExtensionsRoot)
            extensionName.endsWith(".apk") -> installAPK(true, copyToExtensionsRoot)
            else -> throw NullPointerException("Could not find extension type for $extensionName")
        }
    }

    suspend fun installAPK(
        forceReinstall: Boolean = false,
        fetcher: suspend () -> String,
    ): String {
        val apkFile = Path(fetcher())

        val packageInfo = getPackageInfo(apkFile)
        val pkgName = packageInfo.packageName

        // check if we don't have the extension already installed
        // if it's installed and we want to update, it first has to be uninstalled
        val isInstalled =
            transaction {
                ExtensionTable.select(ExtensionTable.isInstalled).where { ExtensionTable.pkgName eq pkgName }.firstOrNull()
            }?.get(ExtensionTable.isInstalled) ?: false

        val dirPathWithoutType = "${applicationDirs.extensionsRoot}/${apkFile.nameWithoutExtension}"
        val jarFile = Path("$dirPathWithoutType.jar")

        if (isInstalled && forceReinstall) {
            uninstallExtension(pkgName)
        }

        if (!isInstalled || forceReinstall) {
            if (!packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }) {
                throw Exception("This apk is not a Tachiyomi extension")
            }

            // Validate lib version
            val libVersion = packageInfo.versionName.substringBeforeLast('.').toDouble()
            if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
                throw Exception(
                    "Lib version is $libVersion, while only versions " +
                        "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed",
                )
            }

            // TODO: allow trusting keys
            // val signatureHash = getSignatureHash(packageInfo)
            //
            // if (signatureHash == null) {
            //     throw Exception("Package $pkgName isn't signed")
            // } else if (signatureHash !in trustedSignatures) {
            //     throw Exception("This apk is not a signed with the official tachiyomi signature")
            // }

            var contentWarning = packageInfo.applicationInfo.metaData.getInt(METADATA_CONTENT_WARNING)
            if (contentWarning == 0) {
                contentWarning = packageInfo.applicationInfo.metaData
                    .getString(METADATA_CONTENT_WARNING)
                    ?.toIntOrNull()
                    ?: 0
                if (contentWarning == 0) {
                    contentWarning = packageInfo.applicationInfo.metaData
                        .getString(METADATA_NSFW)
                        ?.toIntOrNull()
                        ?: 0
                }
            }

            val sourceClass =
                packageInfo.applicationInfo.metaData
                    .getString(METADATA_SOURCE_CLASS)!!
                    .trim()

            val className =
                if (sourceClass.startsWith(".")) {
                    pkgName + sourceClass
                } else {
                    sourceClass
                }

            logger.debug { "Main class for extension is $className" }

            dex2jar(apkFile, jarFile)
            extractAssetsFromApk(apkFile, jarFile)
            extractAndCacheApkIcon(apkFile, pkgName)

            // clean up
            apkFile.deleteIfExists()

            try {
                val extensionName =
                    packageInfo.applicationInfo.metaData.getString(METADATA_NAME)
                        ?: packageInfo.applicationInfo.nonLocalizedLabel
                            .toString()
                            .substringAfter("Tachiyomi: ")

                val extensionLibVersion =
                    packageInfo.applicationInfo.metaData
                        .getString(METADATA_EXTENSION_LIB)
                        .takeUnless { it == "0" }
                        ?: packageInfo.versionName.substringBeforeLast('.')

                setupJar(
                    jarFile,
                    className,
                    extensionName,
                    extensionLibVersion,
                    apkFile.name,
                    pkgName,
                    packageInfo.versionName,
                    packageInfo.versionCode,
                    contentWarning
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
        }
        return pkgName
    }

    suspend fun installJAR(
        forceReinstall: Boolean = false,
        fetcher: suspend () -> String,
    ): String {
        val jarFile = Path(fetcher())

        val jarZip = ZipFile.builder()
            .setPath(jarFile)
            .get()


        return jarZip.use { jarZip ->
            val manifest = jarZip.getInputStream(jarZip.getEntry("AndroidManifest.xml"))
                .use {
                    AndroidManifestParser.parse(it)
                }
            val pkgName = manifest.packageName

            // check if we don't have the extension already installed
            // if it's installed and we want to update, it first has to be uninstalled
            val isInstalled =
                transaction {
                    ExtensionTable.select(ExtensionTable.isInstalled).where { ExtensionTable.pkgName eq pkgName }.firstOrNull()
                }?.get(ExtensionTable.isInstalled) ?: false

            if (isInstalled && forceReinstall) {
                uninstallExtension(pkgName)
            }

            if (!isInstalled || forceReinstall) {
                if (!manifest.usesFeatures.any { it.name == EXTENSION_FEATURE }) {
                    throw Exception("This apk is not a Tachiyomi extension")
                }

                // Validate lib version
                val libVersion = manifest.versionName!!.substringBeforeLast('.').toDouble()
                if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
                    throw Exception(
                        "Lib version is $libVersion, while only versions " +
                            "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed",
                    )
                }

                // TODO: allow trusting keys
                // val signatureHash = getSignatureHash(packageInfo)
                //
                // if (signatureHash == null) {
                //     throw Exception("Package $pkgName isn't signed")
                // } else if (signatureHash !in trustedSignatures) {
                //     throw Exception("This apk is not a signed with the official tachiyomi signature")
                // }

                fun List<AndroidManifestParser.MetaData>.getString(name: String): String? {
                    return this.find { it.name == name }?.value
                }

                var contentWarning = manifest.application!!.metaData.getString(METADATA_CONTENT_WARNING)
                    ?.toIntOrNull()
                if (contentWarning == null) {
                    contentWarning = manifest.application.metaData.getString(METADATA_NSFW)
                        ?.toIntOrNull()
                        ?: 0
                }

                val sourceClass =
                    manifest.application.metaData.getString(METADATA_SOURCE_CLASS)!!
                        .trim()

                val className =
                    if (sourceClass.startsWith(".")) {
                        pkgName + sourceClass
                    } else {
                        sourceClass
                    }

                logger.debug { "Main class for extension is $className" }

                extractAndCacheJarIcon(jarZip, pkgName)

                try {
                    val extensionName =
                        manifest.application.metaData.getString(METADATA_NAME)
                            ?: manifest.application.label!!
                                .substringAfter("Tachiyomi: ")

                    val extensionLibVersion =
                        manifest.application.metaData
                            .getString(METADATA_EXTENSION_LIB)
                            .takeUnless { it == "0" }
                            ?: manifest.versionName.substringBeforeLast('.')

                    setupJar(
                        jarFile,
                        className,
                        extensionName,
                        extensionLibVersion,
                        jarFile.name.removeSuffix(".jar") + ".apk",
                        pkgName,
                        manifest.versionName,
                        manifest.versionCode!!,
                        contentWarning
                    )
                } catch (e: Throwable) {
                    // free up the file descriptor if exists
                    PackageTools.jarLoaderMap.remove(jarFile.absolutePathString())?.close()
                    jarFile.deleteIfExists()

                    try {
                        uninstallExtension(pkgName)
                    } catch (_: Exception) {}
                    throw e
                }
            }
            return@use pkgName
        }
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
        val iconCacheDir = Path("${applicationDirs.extensionsRoot}/icon")
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
                    assetFile.parent.createDirectories()
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
        tempJarFile.deleteIfExists()

        assetsFolder.deleteRecursively()
    }

    private val network: NetworkHelper by injectLazy()

    private suspend fun downloadExtension(
        url: String,
        savePath: String,
    ) {
        val response =
            network.client
                .newCall(
                    GET(url, cache = CacheControl.FORCE_NETWORK),
                ).await()

        val downloadedFile = Path(savePath)
        response.body.byteStream().use {
            downloadedFile.outputStream().buffered().use { out ->
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

            jarPath.deleteIfExists()
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
