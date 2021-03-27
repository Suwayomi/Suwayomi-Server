package ir.armor.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Level
import eu.kanade.tachiyomi.App
import ir.armor.tachidesk.Main
import ir.armor.tachidesk.database.makeDataBaseTables
import ir.armor.tachidesk.server.util.systemTray
import net.harawata.appdirs.AppDirsFactory
import org.kodein.di.DI
import org.kodein.di.conf.global
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.ts.config.ConfigKodeinModule
import xyz.nulldev.ts.config.GlobalConfigManager
import java.io.File

object applicationDirs {
    val dataRoot = AppDirsFactory.getInstance().getUserDataDir("Tachidesk", null, null)!!
    val extensionsRoot = "$dataRoot/extensions"
    val thumbnailsRoot = "$dataRoot/thumbnails"
    val mangaRoot = "$dataRoot/manga"
}

val serverConfig: ServerConfig by lazy { GlobalConfigManager.module() }

val systemTray by lazy { systemTray() }

val androidCompat by lazy { AndroidCompat() }

fun applicationSetup() {
    // register server config
    GlobalConfigManager.registerModule(
        ServerConfig.register(GlobalConfigManager.config)
    )

    // set application wide logging level
    if (serverConfig.debugLogsEnabled) {
        (mu.KotlinLogging.logger(org.slf4j.Logger.ROOT_LOGGER_NAME).underlyingLogger as ch.qos.logback.classic.Logger).level = Level.DEBUG
    }

    // make dirs we need
    listOf(
        applicationDirs.dataRoot,
        applicationDirs.extensionsRoot,
        "${applicationDirs.extensionsRoot}/icon",
        applicationDirs.thumbnailsRoot
    ).forEach {
        File(it).mkdirs()
    }

    // create conf file if doesn't exist
    try {
        val dataConfFile = File("${applicationDirs.dataRoot}/server.conf")
        if (!dataConfFile.exists()) {
            val inpStream = File(
                Main::class.java.getResource("/server-reference.conf").toURI()
            ).inputStream()
            val outStream = dataConfFile.outputStream()

            inpStream.copyTo(outStream)

            inpStream.close()
            outStream.close()
        }
    } catch (e: Exception) {}

    makeDataBaseTables()

    // create system tray
    if (serverConfig.systemTrayEnabled)
        try {
            systemTray
        } catch (e: Exception) {
            e.printStackTrace()
        }

    // Load config API
    DI.global.addImport(ConfigKodeinModule().create())
    // Load Android compatibility dependencies
    AndroidCompatInitializer().init()
    // start app
    androidCompat.startApp(App())

    // Disable jetty's logging
    System.setProperty("org.eclipse.jetty.util.log.announce", "false")
    System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog")
    System.setProperty("org.eclipse.jetty.LEVEL", "OFF")

    // socks proxy settings
    System.getProperties()["proxySet"] = serverConfig.socksProxy.toString()
    System.getProperties()["socksProxyHost"] = serverConfig.socksProxyHost
    System.getProperties()["socksProxyPort"] = serverConfig.socksProxyPort
}
