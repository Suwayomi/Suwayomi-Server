package suwayomi.tachidesk.server.util.systemtray

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

object SystemTray {
    private val handler: SystemTrayHandler =
        if (System.getProperty("os.name").startsWith("Windows")) {
            WindowsAwtSystemTrayHandler()
        } else {
            DorkboxSystemTrayHandler()
        }

    fun create() = handler.create()

    fun remove() = handler.remove()
}
