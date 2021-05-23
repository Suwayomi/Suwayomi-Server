package ir.armor.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import dorkbox.util.Desktop
import ir.armor.tachidesk.server.serverConfig

object Browser {
    private val appIP = if (serverConfig.ip == "0.0.0.0") "127.0.0.1" else serverConfig.ip
    private val appBaseUrl = "http://$appIP:${serverConfig.port}"

    private val webViewInstances = mutableListOf<Any>()

    fun openInBrowser() {

        val openInWebView = System.getProperty("ir.armor.tachidesk.openInWebview")?.toBoolean()

        if (openInWebView == true) {
            try {
                // TODO
            } catch (e: Throwable) { // cover both java.lang.Exception and java.lang.Error
                e.printStackTrace()
            }
        } else {
            try {
                Desktop.browseURL(appBaseUrl)
            } catch (e: Throwable) { // cover both java.lang.Exception and java.lang.Error
                e.printStackTrace()
            }
        }
    }
}
