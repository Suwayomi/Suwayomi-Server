package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.source.local.LocalSource
import io.javalin.plugin.json.JavalinJackson
import io.javalin.plugin.json.JsonMapper
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.conf.global
import org.kodein.di.singleton
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
import java.io.File
import java.util.Locale

private val logger = KotlinLogging.logger {}

class ApplicationDirs(
    val dataRoot: String = ApplicationRootDir
) {
    val extensionsRoot = "$dataRoot/extensions"
    val thumbnailsRoot = "$dataRoot/thumbnails"
    val mangaDownloadsRoot = "$dataRoot/downloads"
    val localMangaRoot = "$dataRoot/local"
    val webUIRoot = "$dataRoot/webUI"
}

val serverConfig: ServerConfig by lazy { GlobalConfigManager.module() }

val systemTrayInstance by lazy { systemTray() }

val androidCompat by lazy { AndroidCompat() }

fun applicationSetup() {
    logger.info("Running Tachidesk ${BuildConfig.VERSION} revision ${BuildConfig.REVISION}")

    // Application dirs
    val applicationDirs = ApplicationDirs()

    DI.global.addImport(
        DI.Module("Server") {
            bind<ApplicationDirs>() with singleton { applicationDirs }
            bind<IUpdater>() with singleton { Updater() }
            bind<JsonMapper>() with singleton { JavalinJackson() }
            bind<Json>() with singleton { Json { ignoreUnknownKeys = true } }
        }
    )

    logger.debug("Data Root directory is set to: ${applicationDirs.dataRoot}")

    // Migrate Directories from old versions
    File("$ApplicationRootDir/manga-thumbnails").renameTo(applicationDirs.thumbnailsRoot)
    File("$ApplicationRootDir/manga-local").renameTo(applicationDirs.localMangaRoot)
    File("$ApplicationRootDir/manga").renameTo(applicationDirs.mangaDownloadsRoot)
    File("$ApplicationRootDir/anime-thumbnails").delete()

    // make dirs we need
    listOf(
        applicationDirs.dataRoot,
        applicationDirs.extensionsRoot,
        applicationDirs.extensionsRoot + "/icon",
        applicationDirs.thumbnailsRoot,
        applicationDirs.mangaDownloadsRoot,
        applicationDirs.localMangaRoot,
    ).forEach {
        File(it).mkdirs()
    }

    // register Tachidesk's config which is dubbed "ServerConfig"
    GlobalConfigManager.registerModule(
        ServerConfig.register(GlobalConfigManager.config)
    )

    // Make sure only one instance of the app is running
    handleAppMutex()

    // Load config API
    DI.global.addImport(ConfigKodeinModule().create())
    // Load Android compatibility dependencies
    AndroidCompatInitializer().init()
    // start app
    androidCompat.startApp(App())

    // create conf file if doesn't exist
    try {
        val dataConfFile = File("${applicationDirs.dataRoot}/server.conf")
        if (!dataConfFile.exists()) {
            JavalinSetup::class.java.getResourceAsStream("/server-reference.conf").use { input ->
                dataConfFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
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
    if (serverConfig.systemTrayEnabled) {
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
    if (serverConfig.socksProxyEnabled) {
        System.getProperties()["socksProxyHost"] = serverConfig.socksProxyHost
        System.getProperties()["socksProxyPort"] = serverConfig.socksProxyPort
        logger.info("Socks Proxy is enabled to ${serverConfig.socksProxyHost}:${serverConfig.socksProxyPort}")
    }
}
