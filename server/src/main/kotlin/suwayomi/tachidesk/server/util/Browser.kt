package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import dorkbox.desktop.Desktop
import suwayomi.tachidesk.server.serverConfig

object Browser {
    private val electronInstances = mutableListOf<Any>()

    private fun getAppBaseUrl(): String {
        val appIP = if (serverConfig.ip.value == "0.0.0.0") "127.0.0.1" else serverConfig.ip.value
        return "http://$appIP:${serverConfig.port.value}"
    }

    fun openInBrowser() {
        if (serverConfig.webUIEnabled.value) {
            val appBaseUrl = getAppBaseUrl()

            if (serverConfig.webUIInterface.value == WebUIInterface.ELECTRON.name.lowercase()) {
                try {
                    val electronPath = serverConfig.electronPath.value
                    electronInstances.add(ProcessBuilder(electronPath, appBaseUrl).start())
                } catch (e: Throwable) {
                    // cover both java.lang.Exception and java.lang.Error
                    e.printStackTrace()
                }
            } else {
                try {
                    Desktop.browseURL(appBaseUrl)
                } catch (e: Throwable) {
                    // cover both java.lang.Exception and java.lang.Error
                    e.printStackTrace()
                }
            }
        }
    }
}
