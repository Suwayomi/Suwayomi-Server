package suwayomi.tachidesk.test

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
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.BeforeAll
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.conf.global
import org.kodein.di.singleton
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.TestUpdater
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.JavalinSetup
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.androidCompat
import suwayomi.tachidesk.server.database.databaseUp
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.AppMutex
import suwayomi.tachidesk.server.util.SystemTray
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.ts.config.CONFIG_PREFIX
import xyz.nulldev.ts.config.ConfigKodeinModule
import xyz.nulldev.ts.config.GlobalConfigManager
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

            DI.global.addImport(
                DI.Module("Server") {
                    bind<ApplicationDirs>() with singleton { applicationDirs }
                    bind<JsonMapper>() with singleton { JavalinJackson() }
                    bind<IUpdater>() with singleton { TestUpdater() }
                },
            )

            logger.debug("Data Root directory is set to: ${applicationDirs.dataRoot}")

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

            // Make sure only one instance of the app is running
            AppMutex.handleAppMutex()

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
                logger.info("Socks Proxy is enabled to ${serverConfig.socksProxyHost.value}:${serverConfig.socksProxyPort.value}")
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
