package suwayomi.tachidesk.test

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.createAppModule
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.local.LocalSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.BeforeAll
import org.koin.core.context.startKoin
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.JavalinSetup
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.androidCompat
import suwayomi.tachidesk.server.database.databaseUp
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.serverModule
import suwayomi.tachidesk.server.util.AppMutex.handleAppMutex
import suwayomi.tachidesk.server.util.SystemTray
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.androidcompat.androidCompatModule
import xyz.nulldev.ts.config.CONFIG_PREFIX
import xyz.nulldev.ts.config.GlobalConfigManager
import xyz.nulldev.ts.config.configManagerModule
import java.io.File
import java.util.Locale

open class ApplicationTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            if (!initializedTheApp) {
                val dataRoot = File(BASE_PATH).absolutePath
                System.setProperty("$CONFIG_PREFIX.server.rootDir", dataRoot)

                testingSetup()

                databaseSetup()

                initializedTheApp = true
            }
        }

        private val logger = KotlinLogging.logger {}
        private var initializedTheApp = false

        fun testingSetup() {
            // Application dirs
            val applicationDirs = ApplicationDirs()

            logger.debug { "Data Root directory is set to: ${applicationDirs.dataRoot}" }

            // make dirs we need
            listOf(
                applicationDirs.dataRoot,
                applicationDirs.extensionsRoot,
                applicationDirs.extensionsRoot + "/icon",
                applicationDirs.tempThumbnailCacheRoot,
                applicationDirs.downloadsRoot,
                applicationDirs.localMangaRoot,
            ).forEach {
                File(it).mkdirs()
            }

            // register Tachidesk's config which is dubbed "ServerConfig"
            GlobalConfigManager.registerModule(
                ServerConfig.register { GlobalConfigManager.config },
            )

            // initialize Koin modules
            val app = App()
            startKoin {
                modules(
                    createAppModule(app),
                    androidCompatModule(),
                    configManagerModule(),
                    serverModule(applicationDirs),
                )
            }

            // Make sure only one instance of the app is running
            handleAppMutex()

            // Load Android compatibility dependencies
            AndroidCompatInitializer().init()
            // start app
            androidCompat.startApp(app)

            // Initialize NetworkHelper early
            Injekt.get<NetworkHelper>()

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
                logger.error(e) { "Exception while creating initial server.conf" }
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
                logger.error(e) { "Exception while copying Local source's icon" }
            }

            // create system tray
            if (serverConfig.systemTrayEnabled.value) {
                try {
                    SystemTray.create()
                } catch (e: Throwable) {
                    // cover both java.lang.Exception and java.lang.Error
                    e.printStackTrace()
                }
            }

            // Disable jetty's logging
            System.setProperty("org.eclipse.jetty.util.log.announce", "false")
            System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog")
            System.setProperty("org.eclipse.jetty.LEVEL", "OFF")

            // socks proxy settings
            if (serverConfig.socksProxyEnabled.value) {
                System.getProperties()["socksProxyHost"] = serverConfig.socksProxyHost.value
                System.getProperties()["socksProxyPort"] = serverConfig.socksProxyPort.value
                logger.info { "Socks Proxy is enabled to ${serverConfig.socksProxyHost.value}:${serverConfig.socksProxyPort.value}" }
            }
        }

        fun databaseSetup() {
            // fixes #119 , ref: https://github.com/Suwayomi/Suwayomi-Server/issues/119#issuecomment-894681292 , source Id calculation depends on String.lowercase()
            Locale.setDefault(Locale.ENGLISH)

            // in-memory database, don't discard database between connections/transactions
            val db = Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", "org.h2.Driver")

            databaseUp(db)

            LocalSource.register()
        }
    }
}
