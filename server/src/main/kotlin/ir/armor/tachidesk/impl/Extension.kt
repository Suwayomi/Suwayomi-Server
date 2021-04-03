package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.net.Uri
import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.impl.ExtensionsList.extensionTableAsDataClass
import ir.armor.tachidesk.impl.util.APKExtractor
import ir.armor.tachidesk.impl.util.CachedImageResponse.getCachedImageResponse
import ir.armor.tachidesk.impl.util.await
import ir.armor.tachidesk.model.database.ExtensionTable
import ir.armor.tachidesk.model.database.SourceTable
import ir.armor.tachidesk.server.ApplicationDirs
import mu.KotlinLogging
import okhttp3.Request
import okio.buffer
import okio.sink
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

object Extension {
    private val logger = KotlinLogging.logger {}

    /**
     * Convert dex to jar, a wrapper for the dex2jar library
     */
    private fun dex2jar(dexFile: String, jarFile: String, fileNameWithoutType: String) {
        // adopted from com.googlecode.dex2jar.tools.Dex2jarCmd.doCommandLine
        // source at: https://github.com/DexPatcher/dex2jar/tree/v2.1-20190905-lanchon/dex-tools/src/main/java/com/googlecode/dex2jar/tools/Dex2jarCmd.java

        val jarFilePath = File(jarFile).toPath()
        val reader = MultiDexFileReader.open(Files.readAllBytes(File(dexFile).toPath()))
        val handler = BaksmaliBaseDexExceptionHandler()
        Dex2jar
            .from(reader)
            .withExceptionHandler(handler)
            .reUseReg(false)
            .topoLogicalSort()
            .skipDebug(true)
            .optimizeSynchronized(false)
            .printIR(false)
            .noCode(false)
            .skipExceptions(false)
            .to(jarFilePath)
        if (handler.hasException()) {
            val errorFile: Path = File(ApplicationDirs.extensionsRoot).toPath().resolve("$fileNameWithoutType-error.txt")
            logger.error(
                "Detail Error Information in File $errorFile\n" +
                    "Please report this file to one of following link if possible (any one).\n" +
                    "    https://sourceforge.net/p/dex2jar/tickets/\n" +
                    "    https://bitbucket.org/pxb1988/dex2jar/issues\n" +
                    "    https://github.com/pxb1988/dex2jar/issues\n" +
                    "    dex2jar@googlegroups.com"
            )
            handler.dump(errorFile, emptyArray<String>())
        }
    }

    /**
     * loads the extension main class called $className from the jar located at $jarPath
     * It may return an instance of HttpSource or SourceFactory depending on the extension.
     */
    fun loadExtensionInstance(jarPath: String, className: String): Any {
        val classLoader = URLClassLoader(arrayOf<URL>(URL("file:$jarPath")))
        val classToLoad = Class.forName(className, true, classLoader)
        return classToLoad.getDeclaredConstructor().newInstance()
    }

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
            val apkSavePath = "${ApplicationDirs.extensionsRoot}/$apkName"
            // download apk file
            downloadAPKFile(apkURL, apkSavePath)

            apkSavePath
        }
    }

    suspend fun installAPK(fetcher: suspend () -> String): Int {
        val apkFilePath = fetcher()
        val apkName = Uri.parse(apkFilePath).lastPathSegment!!

        // TODO: handle the whole apk signature, and trusting bossiness

        val extensionRecord: ResultRow = transaction {
            ExtensionTable.select { ExtensionTable.apkName eq apkName }.firstOrNull()
        } ?: {
            ExtensionTable.insert {
                it[this.apkName] = apkName
            }
            ExtensionTable.select { ExtensionTable.apkName eq apkName }.firstOrNull()!!
        }()

        val extensionId = extensionRecord[ExtensionTable.id]

        // check if we don't have the extension already installed
        if (!extensionRecord[ExtensionTable.isInstalled]) {
            val fileNameWithoutType = apkName.substringBefore(".apk")

            val dirPathWithoutType = "${ApplicationDirs.extensionsRoot}/$fileNameWithoutType"
            val jarFilePath = "$dirPathWithoutType.jar"
            val dexFilePath = "$dirPathWithoutType.dex"

            val className: String = APKExtractor.extractDexAndReadClassname(apkFilePath, dexFilePath)
            logger.debug("Main class for extension is $className")

            dex2jar(dexFilePath, jarFilePath, fileNameWithoutType)

            // clean up
            File(apkFilePath).delete()
            File(dexFilePath).delete()

            // update sources of the extension
            val instance = loadExtensionInstance(jarFilePath, className)

            when (instance) {
                is HttpSource -> { // single source
                    transaction {
                        if (SourceTable.select { SourceTable.id eq instance.id }.count() == 0L) {
                            SourceTable.insert {
                                it[id] = instance.id
                                it[name] = instance.name
                                it[lang] = instance.lang
                                it[extension] = extensionId
                            }
                        }
                        logger.debug("Installed source ${instance.name} with id ${instance.id}")
                    }
                }
                is SourceFactory -> { // theme source or multi lang
                    transaction {
                        instance.createSources().forEach { source ->
                            val httpSource = source as HttpSource
                            if (SourceTable.select { SourceTable.id eq httpSource.id }.count() == 0L) {
                                SourceTable.insert {
                                    it[id] = httpSource.id
                                    it[name] = httpSource.name
                                    it[lang] = httpSource.lang
                                    it[extension] = extensionId
                                    it[partOfFactorySource] = true
                                }
                            }
                            logger.debug("Installed source ${httpSource.name} with id:${httpSource.id}")
                        }
                    }
                }
                else -> {
                    throw RuntimeException("Extension content is unexpected")
                }
            }

            // update extension info
            transaction {
                ExtensionTable.update({ ExtensionTable.apkName eq apkName }) {
                    it[isInstalled] = true
                    it[classFQName] = className
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

        val extensionRecord = transaction { ExtensionTable.select { ExtensionTable.pkgName eq pkgName }.firstOrNull()!! }
        val fileNameWithoutType = extensionRecord[ExtensionTable.apkName].substringBefore(".apk")
        val jarPath = "${ApplicationDirs.extensionsRoot}/$fileNameWithoutType.jar"
        transaction {
            val extensionId = extensionRecord[ExtensionTable.id].value

            SourceTable.deleteWhere { SourceTable.extension eq extensionId }
            if (extensionRecord[ExtensionTable.isObsolete])
                ExtensionTable.deleteWhere { ExtensionTable.pkgName eq pkgName }
            else
                ExtensionTable.update({ ExtensionTable.pkgName eq pkgName }) {
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
            ExtensionTable.update({ ExtensionTable.pkgName eq pkgName }) {
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
        val iconUrl = transaction { ExtensionTable.select { ExtensionTable.apkName eq apkName }.firstOrNull()!! }[ExtensionTable.iconUrl]

        val saveDir = "${ApplicationDirs.extensionsRoot}/icon"

        return getCachedImageResponse(saveDir, apkName) {
            network.client.newCall(
                GET(iconUrl)
            ).await()
        }
    }

    fun getExtensionIconUrl(apkName: String): String {
        return "/api/v1/extension/icon/$apkName"
    }
}
