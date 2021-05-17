package ir.armor.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.typesafe.config.Config
import io.github.config4k.getValue
import xyz.nulldev.ts.config.ConfigModule

class ServerConfig(config: Config) : ConfigModule(config) {
    val ip: String by config
    val port: Int by config

    // proxy
    val socksProxyEnabled: Boolean by config
    val socksProxyHost: String by config
    val socksProxyPort: String by config

    // misc
    val debugLogsEnabled: Boolean = System.getProperty("ir.armor.tachidesk.debugLogsEnabled", config.getString("debugLogsEnabled")).toBoolean()
    val systemTrayEnabled: Boolean by config
    val initialOpenInBrowserEnabled: Boolean by config

    companion object {
        fun register(config: Config) = ServerConfig(config.getConfig("server"))
    }
}
