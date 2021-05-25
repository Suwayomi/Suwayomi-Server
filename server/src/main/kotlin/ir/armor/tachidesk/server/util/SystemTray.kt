package ir.armor.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import dorkbox.util.CacheUtil
import ir.armor.tachidesk.server.BuildConfig
import ir.armor.tachidesk.server.ServerConfig
import ir.armor.tachidesk.server.serverConfig
import ir.armor.tachidesk.server.util.Browser.openInBrowser
import ir.armor.tachidesk.server.util.ExitCode.Success

object SystemTray {
    fun systemTray(): SystemTray? {
        try {
            // ref: https://github.com/dorkbox/SystemTray/blob/master/test/dorkbox/TestTray.java
            SystemTray.DEBUG = serverConfig.debugLogsEnabled

            CacheUtil.clear(BuildConfig.NAME)

            val systemTray = SystemTray.get(BuildConfig.NAME) ?: return null
            val mainMenu = systemTray.menu

            mainMenu.add(
                MenuItem(
                    "Open Tachidesk"
                ) {
                    openInBrowser()
                }
            )

            val icon = ServerConfig::class.java.getResource("/icon/faviconlogo.png")

            // systemTray.setTooltip("Tachidesk")
            systemTray.setImage(icon)
            // systemTray.status = "No Mail"

            mainMenu.add(
                MenuItem("Quit") {
                    shutdownApp(Success)
                }
            )

            systemTray.installShutdownHook()

            return systemTray
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
