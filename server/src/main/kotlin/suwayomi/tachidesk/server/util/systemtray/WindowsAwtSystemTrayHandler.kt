package suwayomi.tachidesk.server.util.systemtray

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.util.Browser.openInBrowser
import suwayomi.tachidesk.server.util.ExitCode.Success
import suwayomi.tachidesk.server.util.shutdownApp
import java.awt.AWTException
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.imageio.ImageIO

class WindowsAwtSystemTrayHandler : SystemTrayHandler {
    private val logger = KotlinLogging.logger {}
    private var trayIcon: TrayIcon? = null

    override fun create() {
        if (trayIcon != null) return

        if (!SystemTray.isSupported()) {
            logger.error { "create: SystemTray is not supported on this platform" }
            return
        }

        try {
            val image =
                ServerConfig::class.java
                    .getResourceAsStream("/icon/faviconlogo.png")
                    ?.let { ImageIO.read(it) }
                    ?: run {
                        logger.error { "create: could not load tray icon image" }
                        return
                    }

            val popup = PopupMenu()

            val openItem = MenuItem("Open Suwayomi")
            openItem.addActionListener { openInBrowser() }
            popup.add(openItem)

            popup.addSeparator()

            val quitItem = MenuItem("Quit")
            quitItem.addActionListener { shutdownApp(Success) }
            popup.add(quitItem)

            val icon = TrayIcon(image, BuildConfig.NAME, popup)
            icon.isImageAutoSize = true
            // double-click / left-click opens the browser
            icon.addActionListener { openInBrowser() }

            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        } catch (e: AWTException) {
            logger.error(e) { "create: failed to add tray icon" }
        } catch (e: Exception) {
            logger.error(e) { "create: unexpected error" }
        }
    }

    override fun remove() {
        trayIcon?.let {
            SystemTray.getSystemTray().remove(it)
            trayIcon = null
        }
    }
}
