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
import mu.KotlinLogging
import okhttp3.Request
import okio.buffer
import okio.sink
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.anime.impl.extension.ExtensionsList.extensionTableAsDataClass
import suwayomi.tachidesk.anime.impl.extension.github.ExtensionGithubApi
import suwayomi.tachidesk.anime.impl.util.PackageTools.EXTENSION_FEATURE
import suwayomi.tachidesk.anime.impl.util.PackageTools.LIB_VERSION_MAX
import suwayomi.tachidesk.anime.impl.util.PackageTools.LIB_VERSION_MIN
import suwayomi.tachidesk.anime.impl.util.PackageTools.METADATA_NSFW
import suwayomi.tachidesk.anime.impl.util.PackageTools.METADATA_SOURCE_CLASS
import suwayomi.tachidesk.anime.impl.util.PackageTools.dex2jar
import suwayomi.tachidesk.anime.impl.util.PackageTools.getPackageInfo
import suwayomi.tachidesk.anime.impl.util.PackageTools.getSignatureHash
import suwayomi.tachidesk.anime.impl.util.PackageTools.loadExtensionSources
import suwayomi.tachidesk.anime.impl.util.PackageTools.trustedSignatures
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.anime.model.table.AnimeSourceTable
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getCachedImageResponse
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream

object Extension {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs by DI.global.instance<ApplicationDirs>()

    data class InstallableAPK(
        val apkFilePath: String,
        val pkgName: String
    )

    suspend fun installExtension(pkgName: String): Int {
        logger.debug("Installing $pkgName")
        val extensionRecord = extensionTableAsDataClass().first { it.pkgName == pkgName }

        return installAPK {
            val apkURL = ExtensionGithubApi.getApkUrl(extensionRecord)
            val apkName = Uri.parse(apkURL).lastPathSegment!!
            val apkSavePath = "${applicationDirs.extensionsRoot}/$apkName"
            // download apk file
            downloadAPKFile(apkURL, apkSavePath)

            apkSavePath
        }
    }

    suspend fun installAPK(fetcher: suspend () -> String): Int {
        val apkFilePath = fetcher()
        val apkName = File(apkFilePath).name

        // check if we don't have the extension already installed
        // if it's installed and we want to update, it first has to be uninstalled
        val isInstalled = transaction {
            AnimeExtensionTable.select { AnimeExtensionTable.apkName eq apkName }.firstOrNull()
        }?.get(AnimeExtensionTable.isInstalled) ?: false

        if (!isInstalled) {
            val fileNameWithoutType = apkName.substringBefore(".apk")

            val dirPathWithoutType = "${applicationDirs.extensionsRoot}/$fileNameWithoutType"
            val jarFilePath = "$dirPathWithoutType.jar"
            val dexFilePath = "$dirPathWithoutType.dex"

            val packageInfo = getPackageInfo(apkFilePath)
            val pkgName = packageInfo.packageName

            if (!packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }) {
                throw Exception("This apk is not a Tachiyomi extension")
            }

            // Validate lib version
            val libVersion = packageInfo.versionName.substringBeforeLast('.').toDouble()
            if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
                throw Exception(
                    "Lib version is $libVersion, while only versions " +
                        "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed"
                )
            }

            val signatureHash = getSignatureHash(packageInfo)

            if (signatureHash == null) {
                throw Exception("Package $pkgName isn't signed")
            } else if (signatureHash !in trustedSignatures) {
                // TODO: allow trusting keys
                throw Exception("This apk is not a signed with the official tachiyomi signature")
            }

            val isNsfw = packageInfo.applicationInfo.metaData.getString(METADATA_NSFW) == "1"

            val className = packageInfo.packageName + packageInfo.applicationInfo.metaData.getString(METADATA_SOURCE_CLASS)

            logger.debug("Main class for extension is $className")

            dex2jar(apkFilePath, jarFilePath, fileNameWithoutType)

            // clean up
//            File(apkFilePath).delete()
            File(dexFilePath).delete()

            // collect sources from the extension
            val sources: List<AnimeCatalogueSource> = when (val instance = loadExtensionSources(jarFilePath, className)) {
                is AnimeSource -> listOf(instance)
                is AnimeSourceFactory -> instance.createSources()
                else -> throw RuntimeException("Unknown source class type! ${instance.javaClass}")
            }.map { it as AnimeCatalogueSource }

            val langs = sources.map { it.lang }.toSet()
            val extensionLang = when (langs.size) {
                0 -> ""
                1 -> langs.first()
                else -> "all"
            }

            val extensionName = packageInfo.applicationInfo.nonLocalizedLabel.toString().substringAfter("Aniyomi: ")

            // update extension info
            transaction {
                if (AnimeExtensionTable.select { AnimeExtensionTable.pkgName eq pkgName }.firstOrNull() == null) {
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
                    it[this.isInstalled] = true
                    it[this.classFQName] = className
                }

                val extensionId = AnimeExtensionTable.select { AnimeExtensionTable.pkgName eq pkgName }.first()[AnimeExtensionTable.id].value

                sources.forEach { httpSource ->
                    AnimeSourceTable.insert {
                        it[id] = httpSource.id
                        it[name] = httpSource.name
                        it[lang] = httpSource.lang
                        it[extension] = extensionId
                    }
                    logger.debug("Installed source ${httpSource.name} (${httpSource.lang}) with id:${httpSource.id}")
                }
            }
            return 201 // we installed successfully
        } else {
            return 302 // extension was already installed
        }
    }

    private val network: NetworkHelper by injectLazy()

    private suspend fun downloadAPKFile(url: String, savePath: String) {
        val request = Request.Builder().url(url).build()
        val response = network.client.newCall(request).await()

        val downloadedFile = File(savePath)
        downloadedFile.sink().buffer().use { sink ->
            response.body!!.source().use { source ->
                sink.writeAll(source)
                sink.flush()
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        logger.debug("Uninstalling $pkgName")

        val extensionRecord = transaction { AnimeExtensionTable.select { AnimeExtensionTable.pkgName eq pkgName }.first() }
        val fileNameWithoutType = extensionRecord[AnimeExtensionTable.apkName].substringBefore(".apk")
        val jarPath = "${applicationDirs.extensionsRoot}/$fileNameWithoutType.jar"
        transaction {
            val extensionId = extensionRecord[AnimeExtensionTable.id].value

            AnimeSourceTable.deleteWhere { AnimeSourceTable.extension eq extensionId }
            if (extensionRecord[AnimeExtensionTable.isObsolete])
                AnimeExtensionTable.deleteWhere { AnimeExtensionTable.pkgName eq pkgName }
            else
                AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq pkgName }) {
                    it[isInstalled] = false
                }
        }

        if (File(jarPath).exists()) {
            File(jarPath).delete()
        }
    }

    suspend fun updateExtension(pkgName: String): Int {
        val targetExtension = ExtensionsList.updateMap.remove(pkgName)!!
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
        val iconUrl = transaction { AnimeExtensionTable.select { AnimeExtensionTable.apkName eq apkName }.first() }[AnimeExtensionTable.iconUrl]

        val saveDir = "${applicationDirs.extensionsRoot}/icon"

        return getCachedImageResponse(saveDir, apkName) {
            network.client.newCall(
                GET(iconUrl)
            ).await()
        }
    }

    fun getExtensionIconUrl(apkName: String): String {
        return "/api/v1/anime/extension/icon/$apkName"
    }
}
