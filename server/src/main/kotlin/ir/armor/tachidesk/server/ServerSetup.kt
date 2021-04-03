package ir.armor.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import eu.kanade.tachiyomi.App
import ir.armor.tachidesk.Main
import ir.armor.tachidesk.model.dataclass.makeDataBaseTables
import ir.armor.tachidesk.server.util.systemTray
import mu.KotlinLogging
import net.harawata.appdirs.AppDirsFactory
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.conf.global
import org.kodein.di.instance
import org.kodein.di.singleton
import org.slf4j.Logger
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.ts.config.ConfigKodeinModule
import xyz.nulldev.ts.config.GlobalConfigManager
import java.io.File

private val logger = KotlinLogging.logger {}

class ApplicationDirs(
    val dataRoot: String = AppDirsFactory.getInstance().getUserDataDir("Tachidesk", null, null)
) {
    val extensionsRoot = "$dataRoot/extensions"
    val thumbnailsRoot = "$dataRoot/thumbnails"
    val mangaRoot = "$dataRoot/manga"
}

val serverConfig: ServerConfig by lazy { GlobalConfigManager.module() }

val systemTray by lazy { systemTray() }

val androidCompat by lazy { AndroidCompat() }

fun applicationSetup(rootDir: String? = null) {
    val dirs = if (rootDir != null) {
        ApplicationDirs(rootDir)
    } else {
        ApplicationDirs()
    }

    System.setProperty("ir.armor.tachidesk.rootDir", dirs.dataRoot)

    // make dirs we need
    listOf(
        dirs.dataRoot,
        dirs.extensionsRoot,
        dirs.extensionsRoot + "/icon",
        dirs.thumbnailsRoot
    ).forEach {
        File(it).mkdirs()
    }

    GlobalConfigManager.registerModule(
        ServerConfig.register(GlobalConfigManager.config)
    )

    // Application dirs
    DI.global.addImport(DI.Module("Server") {
        bind<ApplicationDirs>() with singleton { dirs }
    })
    // Load config API
    DI.global.addImport(ConfigKodeinModule().create())
    // Load Android compatibility dependencies
    AndroidCompatInitializer().init()
    // start app
    androidCompat.startApp(App())

    // set application wide logging level
    if (serverConfig.debugLogsEnabled) {
        (KotlinLogging.logger(Logger.ROOT_LOGGER_NAME).underlyingLogger as ch.qos.logback.classic.Logger).level = Level.DEBUG
    }

    // create conf file if doesn't exist
    try {
        val dataConfFile = File("${dirs.dataRoot}/server.conf")
        if (!dataConfFile.exists()) {
            Main::class.java.getResourceAsStream("/server-reference.conf").use { input ->
                dataConfFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    } catch (e: Exception) {
        logger.error("Exception while creating initial server.conf:\n", e)
    }

    makeDataBaseTables()

    // create system tray
    if (serverConfig.systemTrayEnabled) {
        try {
            systemTray
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Disable jetty's logging
    System.setProperty("org.eclipse.jetty.util.log.announce", "false")
    System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog")
    System.setProperty("org.eclipse.jetty.LEVEL", "OFF")

    // socks proxy settings
    if (serverConfig.socksProxyEnabled) {
//        System.getProperties()["proxySet"] = "true"
        System.getProperties()["socksProxyHost"] = serverConfig.socksProxyHost
        System.getProperties()["socksProxyPort"] = serverConfig.socksProxyPort
        logger.info("Socks Proxy is enabled to ${serverConfig.socksProxyHost}:${serverConfig.socksProxyPort}")
    }
}
