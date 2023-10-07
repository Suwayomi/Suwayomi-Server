package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import dorkbox.util.CacheUtil
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.Browser.openInBrowser
import suwayomi.tachidesk.server.util.ExitCode.Success

object SystemTray {
    private var instance: SystemTray? = null

    fun create() {
        instance =
            try {
                // ref: https://github.com/dorkbox/SystemTray/blob/master/test/dorkbox/TestTray.java
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
                    MenuItem(
                        "Open Tachidesk",
                    ) {
                        openInBrowser()
                    },
                )

                val icon = ServerConfig::class.java.getResource("/icon/faviconlogo.png")

                // systemTray.setTooltip("Tachidesk")
                systemTray.setImage(icon)
                // systemTray.status = "No Mail"

                mainMenu.add(
                    MenuItem("Quit") {
                        shutdownApp(Success)
                    },
                )

                systemTray
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
    }

    fun remove() {
        instance?.remove()
        instance = null
    }
}
