package ir.armor.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import dorkbox.systemTray.SystemTray.TrayType
import ir.armor.tachidesk.Main
import ir.armor.tachidesk.server.serverConfig
import java.awt.Desktop
import java.net.URI
import kotlin.system.exitProcess

fun openInBrowser() {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI("http://127.0.0.1:4567"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun systemTray(): SystemTray? {
    try {
        // ref: https://github.com/dorkbox/SystemTray/blob/master/test/dorkbox/TestTray.java
        SystemTray.DEBUG = serverConfig.debugLogsEnabled
        if (System.getProperty("os.name").startsWith("Windows"))
            SystemTray.FORCE_TRAY_TYPE = TrayType.Swing

        val systemTray = SystemTray.get() ?: return null
        val mainMenu = systemTray.menu

        mainMenu.add(
            MenuItem(
                "Open Tachidesk"
            ) {
                openInBrowser()
            }
        )

        val icon = Main::class.java.getResource("/icon/faviconlogo.png")

//    systemTray.setTooltip("Tachidesk")
        systemTray.setImage(icon)
//    systemTray.status = "No Mail"

        mainMenu.add(
            MenuItem("Quit") {
                systemTray.shutdown()
                exitProcess(0)
            }
        )

        return systemTray
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
