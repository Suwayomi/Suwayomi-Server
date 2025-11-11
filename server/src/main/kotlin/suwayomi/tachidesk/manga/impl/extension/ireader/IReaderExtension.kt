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
import org.ireader.core_api.source.CatalogSource
import org.ireader.core_api.source.Source
import org.ireader.core_api.source.SourceFactory
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream

object IReaderExtension {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()
    
    private const val EXTENSION_FEATURE = "ireader.extension"
    private const val METADATA_SOURCE_CLASS = "ireader.extension.class"
    private const val METADATA_NSFW = "ireader.extension.nsfw"
    private const val LIB_VERSION_MIN = 1.0
    private const val LIB_VERSION_MAX = 2.0

    suspend fun installExtension(pkgName: String): Int {
        logger.debug { "Installing IReader extension $pkgName" }
        val extensionRecord = extensionTableAsDataClass().first { it.pkgName == pkgName }

        return installAPK {
            val apkURL = extensionRecord.repo ?: throw NullPointerException("Could not find extension repo")
            val apkName = Uri.parse(apkURL).lastPathSegment!!
            val apkSavePath = "${applicationDirs.extensionsRoot}/ireader/$apkName"
            
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
            if (!packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }) {
                throw Exception("This apk is not an IReader extension")
            }

            val libVersion = packageInfo.versionName.substringBeforeLast('.').toDouble()
            if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
                throw Exception(
                    "Lib version is $libVersion, while only versions " +
                        "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed",
                )
            }

            val isNsfw = packageInfo.applicationInfo.metaData.getString(METADATA_NSFW) == "1"
            val className =
                packageInfo.packageName + packageInfo.applicationInfo.metaData.getString(METADATA_SOURCE_CLASS)

            logger.debug { "Main class for IReader extension is $className" }

            dex2jar(apkFilePath, jarFilePath, fileNameWithoutType)
            extractAssetsFromApk(apkFilePath, jarFilePath)

            File(apkFilePath).delete()
            File(dexFilePath).delete()

            val extensionMainClassInstance = PackageTools.loadExtensionSources(jarFilePath, className)
            val sources: List<CatalogSource> =
                when (extensionMainClassInstance) {
                    is Source -> listOf(extensionMainClassInstance as CatalogSource)
                    is SourceFactory -> extensionMainClassInstance.createSources().map { it as CatalogSource }
                    else -> throw RuntimeException("Unknown source class type! ${extensionMainClassInstance.javaClass}")
                }

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

                sources.forEach { source ->
                    IReaderSourceTable.insert {
                        it[id] = source.id
                        it[name] = source.name
                        it[lang] = source.lang
                        it[extension] = extensionId
                        it[IReaderSourceTable.isNsfw] = isNsfw
                    }
                    logger.debug { "Installed IReader source ${source.name} (${source.lang}) with id:${source.id}" }
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
        val targetExtension = IReaderExtensionsList.updateMap.remove(pkgName)!!
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
}
