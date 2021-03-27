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
import dorkbox.util.CacheUtil
import dorkbox.util.Desktop
import ir.armor.tachidesk.Main
import java.awt.event.ActionListener
import java.io.IOException

fun openInBrowser() {
    try {
        Desktop.browseURL("http://127.0.0.1:4567")
    } catch (e1: IOException) {
        e1.printStackTrace()
    }
}

fun systemTray(): SystemTray? {
    try {
        // ref: https://github.com/dorkbox/SystemTray/blob/master/test/dorkbox/TestTray.java
        SystemTray.DEBUG = false
        if (System.getProperty("os.name").startsWith("Windows"))
            SystemTray.FORCE_TRAY_TYPE = TrayType.Swing

        CacheUtil.clear()

        val systemTray = SystemTray.get() ?: return null
        val mainMenu = systemTray.menu

        mainMenu.add(
            MenuItem(
                "Open Tachidesk",
                ActionListener {
                    try {
                        Desktop.browseURL("http://127.0.0.1:4567")
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            )
        )

        val icon = Main::class.java.getResource("/icon/faviconlogo.png")

//    systemTray.setTooltip("Tachidesk")
        systemTray.setImage(icon)
//    systemTray.status = "No Mail"

        systemTray.getMenu().add(
            MenuItem("Quit") {
                systemTray.shutdown()
                System.exit(0)
            }
        )

        return systemTray
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
