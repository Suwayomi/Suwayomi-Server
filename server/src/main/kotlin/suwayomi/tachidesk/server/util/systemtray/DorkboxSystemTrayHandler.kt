package suwayomi.tachidesk.server.util.systemtray

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import dorkbox.util.CacheUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.Browser.openInBrowser
import suwayomi.tachidesk.server.util.ExitCode.Success
import suwayomi.tachidesk.server.util.shutdownApp

class DorkboxSystemTrayHandler : SystemTrayHandler {
    private val logger = KotlinLogging.logger { }
    private var instance: SystemTray? = null

    override fun create() {
        if (instance != null) return

        instance =
            try {
                serverConfig.subscribeTo(
                    serverConfig.debugLogsEnabled,
                    { debugLogsEnabled -> SystemTray.DEBUG = debugLogsEnabled },
                    ignoreInitialValue = false,
                )

                CacheUtil.clear(BuildConfig.NAME)

                if (System.getProperty("os.name").startsWith("Mac")) {
                    SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Awt
                }

                val systemTray = SystemTray.get(BuildConfig.NAME)
                val mainMenu = systemTray.menu

                mainMenu.add(
                    MenuItem("Open Suwayomi") {
                        openInBrowser()
                    },
                )

                val icon = ServerConfig::class.java.getResource("/icon/faviconlogo.png")
                systemTray.setImage(icon)

                mainMenu.add(
                    MenuItem("Quit") {
                        shutdownApp(Success)
                    },
                )

                systemTray
            } catch (e: Exception) {
                logger.error(e) { "create: failed to create SystemTray due to" }
                null
            }
    }

    override fun remove() {
        instance?.remove()
        instance = null
    }
}
