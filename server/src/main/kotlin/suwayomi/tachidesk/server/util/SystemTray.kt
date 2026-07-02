package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray as DorkboxSystemTray
import dorkbox.util.CacheUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.Browser.openInBrowser
import suwayomi.tachidesk.server.util.ExitCode.Success

object SystemTray {
    private val logger = KotlinLogging.logger { }
    private var instance: DorkboxSystemTray? = null

    fun create() {
        try {
            // ref: https://github.com/dorkbox/SystemTray/blob/master/test/dorkbox/TestTray.java
            serverConfig.subscribeTo(
                serverConfig.debugLogsEnabled,
                { debugLogsEnabled -> DorkboxSystemTray.DEBUG = debugLogsEnabled },
                ignoreInitialValue = false,
            )

            CacheUtil.clear(BuildConfig.NAME)

            val forcedTrayType = resolveForcedTrayType()
            logger.debug { "SystemTray resolved tray type: ${forcedTrayType ?: "AutoDetect"}" }
            forcedTrayType?.let { DorkboxSystemTray.FORCE_TRAY_TYPE = it }

            val systemTray = DorkboxSystemTray.get(BuildConfig.NAME)
            if (systemTray == null) {
                logger.warn { "System tray not supported; disabling tray" }
                instance = null
                return
            }
            val mainMenu = systemTray.menu

            mainMenu.add(
                MenuItem(
                    "Open Suwayomi",
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

            instance = systemTray
        } catch (e: Exception) {
            logger.error(e) { "create: failed to create SystemTray due to" }
            instance = null
        }
    }

    fun remove() {
        instance?.remove()
        instance = null
    }

    private fun resolveForcedTrayType(): DorkboxSystemTray.TrayType? {
        val osName = System.getProperty("os.name")?.lowercase() ?: ""

        if (osName.startsWith("mac")) {
            return DorkboxSystemTray.TrayType.Awt
        }

        if (osName.contains("linux")) {
            val sessionType = System.getenv("XDG_SESSION_TYPE")?.lowercase()
            val waylandDisplay = System.getenv("WAYLAND_DISPLAY")
            val isWayland = sessionType == "wayland" || !waylandDisplay.isNullOrEmpty()

            if (isWayland) {
                val currentDesktop = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase().orEmpty()
                val desktopSession = System.getenv("DESKTOP_SESSION")?.lowercase().orEmpty()
                val isGnome = currentDesktop.contains("gnome") || desktopSession.contains("gnome")

                if (isGnome) {
                    return DorkboxSystemTray.TrayType.AppIndicator
                }
            }
        }

        return null
    }
}
