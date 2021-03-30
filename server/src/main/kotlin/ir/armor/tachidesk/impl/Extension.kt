package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.model.database.ExtensionTable
import ir.armor.tachidesk.model.database.SourceTable
import ir.armor.tachidesk.impl.util.APKExtractor
import ir.armor.tachidesk.server.applicationDirs
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import okhttp3.Request
import okio.buffer
import okio.sink
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
        val errorFile: Path = File(applicationDirs.extensionsRoot).toPath().resolve("$fileNameWithoutType-error.txt")
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

fun installExtension(pkgName: String): Int {
    logger.debug("Installing $pkgName")
    val extensionRecord = extensionTableAsDataClass().first { it.pkgName == pkgName }
    val fileNameWithoutType = extensionRecord.apkName.substringBefore(".apk")
    val dirPathWithoutType = "${applicationDirs.extensionsRoot}/$fileNameWithoutType"

    // check if we don't have the dex file already downloaded
    val jarPath = "${applicationDirs.extensionsRoot}/$fileNameWithoutType.jar"
    if (!File(jarPath).exists()) {
        runBlocking {
            val apkToDownload = ExtensionGithubApi.getApkUrl(extensionRecord)

            val apkFilePath = "$dirPathWithoutType.apk"
            val jarFilePath = "$dirPathWithoutType.jar"
            val dexFilePath = "$dirPathWithoutType.dex"

            // download apk file
            downloadAPKFile(apkToDownload, apkFilePath)

            val className: String = APKExtractor.extractDexAndReadClassname(apkFilePath, dexFilePath)
            logger.debug(className)
            // dex -> jar
            dex2jar(dexFilePath, jarFilePath, fileNameWithoutType)

            // clean up
            File(apkFilePath).delete()
            File(dexFilePath).delete()

            // update sources of the extension
            val instance = loadExtensionInstance(jarFilePath, className)

            val extensionId = transaction {
                ExtensionTable.select { ExtensionTable.name eq extensionRecord.name }.firstOrNull()!![ExtensionTable.id]
            }

            when (instance) {
                is HttpSource -> { // single source
                    transaction {
                        if (SourceTable.select { SourceTable.id eq instance.id }.count() == 0L) {
                            SourceTable.insert {
                                it[this.id] = instance.id
                                it[name] = instance.name
                                it[this.lang] = instance.lang
                                it[extension] = extensionId
                            }
                        }
                        logger.debug("Installed source ${instance.name} with id ${instance.id}")
                    }
                }
                is SourceFactory -> { // theme source or multi lang
                    transaction {
                        instance.createSources().forEachIndexed { index, source ->
                            val httpSource = source as HttpSource
                            if (SourceTable.select { SourceTable.id eq httpSource.id }.count() == 0L) {
                                SourceTable.insert {
                                    it[this.id] = httpSource.id
                                    it[name] = httpSource.name
                                    it[this.lang] = httpSource.lang
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
                ExtensionTable.update({ ExtensionTable.name eq extensionRecord.name }) {
                    it[isInstalled] = true
                    it[classFQName] = className
                }
            }
        }
        return 201 // we downloaded successfully
    } else {
        return 302
    }
}

val networkHelper: NetworkHelper by injectLazy()

private fun downloadAPKFile(url: String, apkPath: String) {
    val request = Request.Builder().url(url).build()
    val response = networkHelper.client.newCall(request).execute()

    val downloadedFile = File(apkPath)
    val sink = downloadedFile.sink().buffer()
    sink.writeAll(response.body!!.source())
    sink.close()
}

fun uninstallExtension(pkgName: String) {
    logger.debug("Uninstalling $pkgName")

    val extensionRecord = transaction { ExtensionTable.select { ExtensionTable.pkgName eq pkgName }.firstOrNull()!! }
    val fileNameWithoutType = extensionRecord[ExtensionTable.apkName].substringBefore(".apk")
    val jarPath = "${applicationDirs.extensionsRoot}/$fileNameWithoutType.jar"
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

fun updateExtension(pkgName: String): Int {
    val targetExtension = ExtensionListData.updateMap.remove(pkgName)!!
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

val network: NetworkHelper by injectLazy()

fun getExtensionIcon(apkName: String): Pair<InputStream, String> {
    val iconUrl = transaction { ExtensionTable.select { ExtensionTable.apkName eq apkName }.firstOrNull()!! }[ExtensionTable.iconUrl]

    val saveDir = "${applicationDirs.extensionsRoot}/icon"

    return getCachedImageResponse(saveDir, apkName) {
        network.client.newCall(
            GET(iconUrl)
        ).execute()
    }
}

fun getExtensionIconUrl(apkName: String): String {
    return "/api/v1/extension/icon/$apkName"
}
