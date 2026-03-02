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
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.Browser.openInBrowser
import suwayomi.tachidesk.server.util.ExitCode.Success

object SystemTray {
    private val logger = KotlinLogging.logger { }
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

                val forcedTrayType = resolveForcedTrayType()
                if (forcedTrayType != null) {
                    SystemTray.FORCE_TRAY_TYPE = forcedTrayType
                }

                val systemTray = SystemTray.get(BuildConfig.NAME)
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

                systemTray
            } catch (e: Exception) {
                logger.error(e) { "create: failed to create SystemTray due to" }
                null
            }
    }

    fun remove() {
        instance?.remove()
        instance = null
    }

    private fun resolveForcedTrayType(): SystemTray.TrayType? {
        val osName = System.getProperty("os.name")?.lowercase() ?: ""

        if (osName.startsWith("mac")) {
            return SystemTray.TrayType.Awt
        }

        if (osName.contains("linux")) {
            val sessionType = System.getenv("XDG_SESSION_TYPE")?.lowercase()
            val waylandDisplay = System.getenv("WAYLAND_DISPLAY")
            if (sessionType == "wayland" || !waylandDisplay.isNullOrEmpty()) {
                return SystemTray.TrayType.Swing
            }
        }

        return null
    }
}
