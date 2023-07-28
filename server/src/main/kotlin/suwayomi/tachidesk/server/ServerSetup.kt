package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.typesafe.config.ConfigRenderOptions
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.source.local.LocalSource
import io.javalin.plugin.json.JavalinJackson
import io.javalin.plugin.json.JsonMapper
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.conf.global
import org.kodein.di.singleton
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.Updater
import suwayomi.tachidesk.manga.impl.util.lang.renameTo
import suwayomi.tachidesk.server.database.databaseUp
import suwayomi.tachidesk.server.util.AppMutex.handleAppMutex
import suwayomi.tachidesk.server.util.SystemTray.systemTray
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.ts.config.ApplicationRootDir
import xyz.nulldev.ts.config.ConfigKodeinModule
import xyz.nulldev.ts.config.GlobalConfigManager
import xyz.nulldev.ts.config.initLoggerConfig
import java.io.File
import java.security.Security
import java.util.Locale

private val logger = KotlinLogging.logger {}

class ApplicationDirs(
    val dataRoot: String = ApplicationRootDir,
    val tempRoot: String = "${System.getProperty("java.io.tmpdir")}/Tachidesk"
) {
    val cacheRoot = System.getProperty("java.io.tmpdir") + "/tachidesk"
    val extensionsRoot = "$dataRoot/extensions"
    val downloadsRoot = serverConfig.downloadsPath.value.ifBlank { "$dataRoot/downloads" }
    val localMangaRoot = serverConfig.localSourcePath.value.ifBlank { "$dataRoot/local" }
    val webUIRoot = "$dataRoot/webUI"
    val automatedBackupRoot = serverConfig.backupPath.value.ifBlank { "$dataRoot/backups" }

    val tempThumbnailCacheRoot = "$tempRoot/thumbnails"
    val tempMangaCacheRoot = "$tempRoot/manga-cache"

    val thumbnailDownloadsRoot = "$downloadsRoot/thumbnails"
    val mangaDownloadsRoot = "$downloadsRoot/mangas"
}

val serverConfig: ServerConfig by lazy { GlobalConfigManager.module() }

val systemTrayInstance by lazy { systemTray() }

val androidCompat by lazy { AndroidCompat() }

fun applicationSetup() {
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
        KotlinLogging.logger { }.error(throwable) { "unhandled exception" }
    }

    // register Tachidesk's config which is dubbed "ServerConfig"
    GlobalConfigManager.registerModule(
        ServerConfig.register { GlobalConfigManager.config }
    )

    // Application dirs
    val applicationDirs = ApplicationDirs()

    initLoggerConfig(applicationDirs.dataRoot)

    logger.info("Running Tachidesk ${BuildConfig.VERSION} revision ${BuildConfig.REVISION}")

    logger.debug {
        "Loaded config:\n" + GlobalConfigManager.config.root().render(ConfigRenderOptions.concise().setFormatted(true))
    }

    val updater = Updater()

    DI.global.addImport(
        DI.Module("Server") {
            bind<ApplicationDirs>() with singleton { applicationDirs }
            bind<IUpdater>() with singleton { updater }
            bind<JsonMapper>() with singleton { JavalinJackson() }
            bind<Json>() with singleton { Json { ignoreUnknownKeys = true } }
        }
    )

    logger.debug("Data Root directory is set to: ${applicationDirs.dataRoot}")

    // Migrate Directories from old versions
    File("$ApplicationRootDir/manga-thumbnails").renameTo(applicationDirs.tempThumbnailCacheRoot)
    File("$ApplicationRootDir/manga-local").renameTo(applicationDirs.localMangaRoot)
    File("$ApplicationRootDir/anime-thumbnails").delete()

    // make dirs we need
    listOf(
        applicationDirs.dataRoot,
        applicationDirs.extensionsRoot,
        applicationDirs.extensionsRoot + "/icon",
        applicationDirs.tempThumbnailCacheRoot,
        applicationDirs.downloadsRoot,
        applicationDirs.localMangaRoot
    ).forEach {
        File(it).mkdirs()
    }

    // Make sure only one instance of the app is running
    handleAppMutex()

    // Load config API
    DI.global.addImport(ConfigKodeinModule().create())
    // Load Android compatibility dependencies
    AndroidCompatInitializer().init()
    // start app
    androidCompat.startApp(App())

    // create or update conf file if doesn't exist
    try {
        val dataConfFile = File("${applicationDirs.dataRoot}/server.conf")
        if (!dataConfFile.exists()) {
            JavalinSetup::class.java.getResourceAsStream("/server-reference.conf").use { input ->
                dataConfFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // make sure the user config file is up-to-date
            GlobalConfigManager.updateUserConfig()
        }
    } catch (e: Exception) {
        logger.error("Exception while creating initial server.conf", e)
    }

    // copy local source icon
    try {
        val localSourceIconFile = File("${applicationDirs.extensionsRoot}/icon/localSource.png")
        if (!localSourceIconFile.exists()) {
            JavalinSetup::class.java.getResourceAsStream("/icon/localSource.png").use { input ->
                localSourceIconFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    } catch (e: Exception) {
        logger.error("Exception while copying Local source's icon", e)
    }

    // fixes #119 , ref: https://github.com/Suwayomi/Tachidesk-Server/issues/119#issuecomment-894681292 , source Id calculation depends on String.lowercase()
    Locale.setDefault(Locale.ENGLISH)

    databaseUp()

    LocalSource.register()

    // create system tray
    if (serverConfig.systemTrayEnabled.value) {
        try {
            systemTrayInstance
        } catch (e: Throwable) { // cover both java.lang.Exception and java.lang.Error
            e.printStackTrace()
        }
    }

    // Disable jetty's logging
    System.setProperty("org.eclipse.jetty.util.log.announce", "false")
    System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog")
    System.setProperty("org.eclipse.jetty.LEVEL", "OFF")

    // socks proxy settings
    serverConfig.subscribeTo(
        combine(
            serverConfig.socksProxyEnabled,
            serverConfig.socksProxyHost,
            serverConfig.socksProxyPort
        ) { proxyEnabled, proxyHost, proxyPort ->
            Triple(proxyEnabled, proxyHost, proxyPort)
        },
        { (proxyEnabled, proxyHost, proxyPort) ->
            logger.info("Socks Proxy changed - enabled= $proxyEnabled, proxy= $proxyHost:$proxyPort")
            if (proxyEnabled) {
                System.getProperties()["socksProxyHost"] = proxyHost
                System.getProperties()["socksProxyPort"] = proxyPort
            } else {
                System.getProperties()["socksProxyHost"] = ""
                System.getProperties()["socksProxyPort"] = ""
            }
        }
    )

    // AES/CBC/PKCS7Padding Cypher provider for zh.copymanga
    Security.addProvider(BouncyCastleProvider())

    // start automated global updates
    updater.scheduleUpdateTask()

    // start automated backups
    ProtoBackupExport.scheduleAutomatedBackupTask()

    // start DownloadManager and restore + resume downloads
    DownloadManager.restoreAndResumeDownloads()
}
