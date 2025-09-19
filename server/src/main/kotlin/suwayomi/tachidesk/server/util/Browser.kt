package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import dorkbox.desktop.Desktop
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.graphql.types.WebUIInterface
import suwayomi.tachidesk.server.serverConfig

object Browser {
    private val logger = KotlinLogging.logger { }
    private val electronInstances = mutableListOf<Any>()

    private fun getAppBaseUrl(): String {
        val appIP = if (serverConfig.ip.value == "0.0.0.0") "127.0.0.1" else serverConfig.ip.value
        val baseUrl = "http://$appIP:${serverConfig.port.value}"
        val subpath = serverConfig.webUISubpath.value
        return if (subpath.isNotBlank()) "$baseUrl$subpath/" else baseUrl
    }

    fun openInBrowser() {
        if (serverConfig.webUIEnabled.value) {
            val appBaseUrl = getAppBaseUrl()

            if (serverConfig.webUIInterface.value == WebUIInterface.ELECTRON) {
                try {
                    val electronPath = serverConfig.electronPath.value
                    electronInstances.add(ProcessBuilder(electronPath, appBaseUrl).start())
                } catch (e: Throwable) {
                    // cover both java.lang.Exception and java.lang.Error
                    logger.error(e) { "openInBrowser: failed to launch electron due to" }
                }
            } else {
                try {
                    Desktop.browseURL(appBaseUrl)
                } catch (e: Throwable) {
                    // cover both java.lang.Exception and java.lang.Error
                    logger.error(e) { "openInBrowser: failed to launch browser due to" }
                }
            }
        }
    }
}
