package suwayomi.tachidesk.anime.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.net.Uri
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
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
import suwayomi.tachidesk.anime.impl.extension.AnimeExtensionsList.extensionTableAsDataClass
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.anime.model.table.AnimeSourceTable
import suwayomi.tachidesk.manga.impl.extension.github.ExtensionGithubApi
import suwayomi.tachidesk.manga.impl.util.PackageTools
import suwayomi.tachidesk.manga.impl.util.PackageTools.EXTENSION_FEATURE
import suwayomi.tachidesk.anime.impl.util.AnimePackageTools.EXTENSION_FEATURE as EXTENSION_FEATURE_ANIME
import suwayomi.tachidesk.anime.impl.util.AnimePackageTools.LIB_VERSION_MAX
import suwayomi.tachidesk.anime.impl.util.AnimePackageTools.LIB_VERSION_MIN
import suwayomi.tachidesk.anime.impl.util.AnimePackageTools.METADATA_NSFW as METADATA_NSFW_ANIME
import suwayomi.tachidesk.anime.impl.util.AnimePackageTools.METADATA_SOURCE_CLASS as METADATA_SOURCE_CLASS_ANIME
import suwayomi.tachidesk.anime.impl.util.AnimePackageTools.METADATA_SOURCE_FACTORY as METADATA_SOURCE_FACTORY_ANIME
import suwayomi.tachidesk.manga.impl.util.PackageTools.EXTENSION_FEATURE as EXTENSION_FEATURE_MANGA
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_NSFW as METADATA_NSFW_MANGA
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_SOURCE_CLASS as METADATA_SOURCE_CLASS_MANGA
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_SOURCE_FACTORY as METADATA_SOURCE_FACTORY_MANGA
import suwayomi.tachidesk.manga.impl.util.PackageTools.dex2jar
import suwayomi.tachidesk.manga.impl.util.PackageTools.getPackageInfo
import suwayomi.tachidesk.manga.impl.util.PackageTools.loadExtensionSources
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo

object AnimeExtension {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()

    suspend fun installExtension(pkgName: String): Int {
        logger.debug { "Installing anime extension $pkgName" }
        val extensionRecord = extensionTableAsDataClass().first { it.pkgName == pkgName }

        return installAPK {
            val apkURL =
                ExtensionGithubApi.getApkUrl(
                    extensionRecord.repo ?: throw NullPointerException("Could not find extension repo"),
                    extensionRecord.apkName,
                )
            val apkName = Uri.parse(apkURL).lastPathSegment!!
            val apkSavePath = "${applicationDirs.extensionsRoot}/$apkName"
            // download apk file
            downloadAPKFile(apkURL, apkSavePath)

            apkSavePath
        }
    }

    suspend fun installExternalExtension(
        inputStream: InputStream,
        apkName: String,
    ): Int =
        installAPK(true) {
            val rootPath = Path(applicationDirs.extensionsRoot)
            val downloadedFile = rootPath.resolve(apkName).normalize()
            check(downloadedFile.startsWith(rootPath) && downloadedFile.parent == rootPath) {
                "File '$apkName' is not a valid extension file"
            }
            logger.debug { "Saving anime extension apk at $apkName" }
            // download apk file
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

        // check if we don't have the extension already installed
        // if it's installed and we want to update, it first has to be uninstalled
        val isInstalled =
            transaction {
                AnimeExtensionTable.selectAll().where { AnimeExtensionTable.apkName eq apkName }.firstOrNull()
            }?.get(AnimeExtensionTable.isInstalled) ?: false

        val fileNameWithoutType = apkName.substringBefore(".apk")

        val dirPathWithoutType = "${applicationDirs.extensionsRoot}/$fileNameWithoutType"
        val jarFilePath = "$dirPathWithoutType.jar"
        val dexFilePath = "$dirPathWithoutType.dex"

        val packageInfo = getPackageInfo(apkFilePath)
        val pkgName = packageInfo.packageName
        if (isInstalled && forceReinstall) {
            uninstallExtension(pkgName)
        }

        if (!isInstalled || forceReinstall) {
            val features = packageInfo.reqFeatures.orEmpty().mapNotNull { it?.name }
            val hasFeature = features.any { it == EXTENSION_FEATURE_ANIME || it == EXTENSION_FEATURE_MANGA }
            val metadataKeys = packageInfo.applicationInfo.metaData?.keySet().orEmpty().toList()
            val hasMetadata =
                metadataKeys.contains(METADATA_SOURCE_CLASS_ANIME) ||
                    metadataKeys.contains(METADATA_SOURCE_FACTORY_ANIME) ||
                    metadataKeys.contains(METADATA_SOURCE_CLASS_MANGA) ||
                    metadataKeys.contains(METADATA_SOURCE_FACTORY_MANGA)
            if (!hasFeature && !hasMetadata) {
                logger.warn {
                    "Anime extension validation failed: features=$features metadataKeys=$metadataKeys"
                }
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

            val metaData = packageInfo.applicationInfo.metaData
                ?: throw Exception("This apk is missing extension metadata")
            val nsfwValue =
                metaData.getString(METADATA_NSFW_ANIME)
                    ?: metaData.getString(METADATA_NSFW_MANGA)
                    ?: "0"
            val isNsfw = nsfwValue == "1"

            val classNameSuffix =
                metaData.getString(METADATA_SOURCE_CLASS_ANIME)
                    ?: metaData.getString(METADATA_SOURCE_CLASS_MANGA)
            if (classNameSuffix.isNullOrBlank()) {
                throw Exception("This apk is missing source class metadata")
            }
            val className = packageInfo.packageName + classNameSuffix

            logger.debug { "Main class for anime extension is $className" }

            dex2jar(apkFilePath, jarFilePath, fileNameWithoutType)
            extractAssetsFromApk(apkFilePath, jarFilePath)

            // clean up
            File(apkFilePath).delete()
            File(dexFilePath).delete()

            // collect sources from the extension
            val extensionMainClassInstance = loadExtensionSources(jarFilePath, className)
            val sources: List<AnimeCatalogueSource> =
                when (extensionMainClassInstance) {
                    is AnimeSource -> listOf(extensionMainClassInstance)
                    is AnimeSourceFactory -> extensionMainClassInstance.createSources()
                    else -> throw RuntimeException("Unknown source class type! ${extensionMainClassInstance.javaClass}")
                }.map { it as AnimeCatalogueSource }

            val langs = sources.map { it.lang }.toSet()
            val extensionLang =
                when (langs.size) {
                    0 -> ""
                    1 -> langs.first()
                    else -> "all"
                }

            val extensionName =
                packageInfo.applicationInfo.nonLocalizedLabel
                    .toString()
                    .substringAfter("Tachiyomi: ")

            // update extension info
            transaction {
                if (AnimeExtensionTable.selectAll().where { AnimeExtensionTable.pkgName eq pkgName }.firstOrNull() == null) {
                    AnimeExtensionTable.insert {
                        it[this.apkName] = apkName
                        it[name] = extensionName
                        it[this.pkgName] = packageInfo.packageName
                        it[versionName] = packageInfo.versionName
                        it[versionCode] = packageInfo.versionCode
                        it[lang] = extensionLang
                        it[this.isNsfw] = isNsfw
                    }
                }

                AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq pkgName }) {
                    it[this.apkName] = apkName
                    it[this.isInstalled] = true
                    it[this.classFQName] = className
                    it[versionName] = packageInfo.versionName
                    it[versionCode] = packageInfo.versionCode
                }

                val extensionId =
                    AnimeExtensionTable
                        .selectAll()
                        .where { AnimeExtensionTable.pkgName eq pkgName }
                        .first()[AnimeExtensionTable.id]
                        .value

                sources.forEach { httpSource ->
                    AnimeSourceTable.insert {
                        it[id] = httpSource.id
                        it[name] = httpSource.name
                        it[lang] = httpSource.lang
                        it[extension] = extensionId
                        it[AnimeSourceTable.isNsfw] = isNsfw
                    }
                    logger.debug { "Installed anime source ${httpSource.name} (${httpSource.lang}) with id:${httpSource.id}" }
                }
            }
            return 201 // we installed successfully
        } else {
            return 302 // extension was already installed
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

        jarFile.delete()
        tempJarFile.renameTo(jarFile)

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
        logger.debug { "Uninstalling anime extension $pkgName" }

        val extensionRecord = transaction { AnimeExtensionTable.selectAll().where { AnimeExtensionTable.pkgName eq pkgName }.first() }
        val fileNameWithoutType = extensionRecord[AnimeExtensionTable.apkName].substringBefore(".apk")
        val jarPath = "${applicationDirs.extensionsRoot}/$fileNameWithoutType.jar"
        transaction {
            val extensionId = extensionRecord[AnimeExtensionTable.id].value

            AnimeSourceTable.deleteWhere { AnimeSourceTable.extension eq extensionId }

            if (extensionRecord[AnimeExtensionTable.isObsolete]) {
                AnimeExtensionTable.deleteWhere { AnimeExtensionTable.pkgName eq pkgName }
            } else {
                AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq pkgName }) {
                    it[isInstalled] = false
                }
            }
        }

        if (File(jarPath).exists()) {
            // free up the file descriptor if exists
            PackageTools.jarLoaderMap.remove(jarPath)?.close()

            File(jarPath).delete()
        }
    }

    suspend fun updateExtension(pkgName: String): Int {
        val targetExtension = AnimeExtensionsList.updateMap.remove(pkgName)!!
        uninstallExtension(pkgName)
        transaction {
            AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq pkgName }) {
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
            transaction { AnimeExtensionTable.selectAll().where { AnimeExtensionTable.apkName eq apkName }.firstOrNull() }
                ?.get(AnimeExtensionTable.iconUrl)
                .orEmpty()

        val cacheSaveDir = "${applicationDirs.extensionsRoot}/icon"

        return getImageResponse(cacheSaveDir, apkName) {
            network.client
                .newCall(
                    GET(iconUrl, cache = CacheControl.FORCE_NETWORK),
                ).await()
        }
    }

    fun getExtensionIconUrl(apkName: String): String = "/api/v1/anime/extension/icon/$apkName"
}
