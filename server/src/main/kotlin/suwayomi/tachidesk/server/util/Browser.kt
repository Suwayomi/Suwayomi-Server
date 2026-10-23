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
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.io.File

object Browser {
    private val logger = KotlinLogging.logger { }
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val electronInstances = mutableListOf<Any>()

    /**
     * Writes the electron entry point to disk, since electron can't load it from inside the jar.
     * Without an entry point, electron's default app is used, which prompts for every permission request.
     */
    private fun extractElectronMainScript(): String {
        val mainScript = File(applicationDirs.tempRoot, "electron/main.js")
        mainScript.parentFile.mkdirs()
        Browser::class.java.getResourceAsStream("/electron/main.js")!!.use { input ->
            mainScript.outputStream().use { output -> input.copyTo(output) }
        }

        return mainScript.absolutePath
    }

    private fun getAppBaseUrl(): String {
        val appIP = if (serverConfig.ip.value == "0.0.0.0") "127.0.0.1" else serverConfig.ip.value
        val baseUrl = "http://$appIP:${serverConfig.port.value}"

        return ServerSubpath.maybeAddAsSuffix(baseUrl)
    }

    fun openInBrowser() {
        if (serverConfig.webUIEnabled.value) {
            val appBaseUrl = getAppBaseUrl()

            if (serverConfig.webUIInterface.value == WebUIInterface.ELECTRON) {
                try {
                    val electronPath = serverConfig.electronPath.value
                    electronInstances.add(ProcessBuilder(electronPath, extractElectronMainScript(), appBaseUrl).start())
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
