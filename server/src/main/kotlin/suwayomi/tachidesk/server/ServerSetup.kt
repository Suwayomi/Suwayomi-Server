package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.App
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.conf.global
import org.kodein.di.singleton
import suwayomi.tachidesk.server.database.databaseUp
import suwayomi.tachidesk.server.util.AppMutex.handleAppMutex
import suwayomi.tachidesk.server.util.SystemTray.systemTray
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.ts.config.ApplicationRootDir
import xyz.nulldev.ts.config.ConfigKodeinModule
import xyz.nulldev.ts.config.GlobalConfigManager
import java.io.File

private val logger = KotlinLogging.logger {}

class ApplicationDirs(
    val dataRoot: String = ApplicationRootDir
) {
    val extensionsRoot = "$dataRoot/extensions"
    val mangaThumbnailsRoot = "$dataRoot/manga-thumbnails"
    val animeThumbnailsRoot = "$dataRoot/anime-thumbnails"
    val mangaRoot = "$dataRoot/manga"
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
        }
    )

    logger.debug("Data Root directory is set to: ${applicationDirs.dataRoot}")

    // make dirs we need
    listOf(
        applicationDirs.dataRoot,
        applicationDirs.extensionsRoot,
        applicationDirs.extensionsRoot + "/icon",
        applicationDirs.mangaThumbnailsRoot,
        applicationDirs.animeThumbnailsRoot,
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
        logger.error("Exception while creating initial server.conf:\n", e)
    }

    databaseUp()

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
