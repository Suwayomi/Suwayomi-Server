package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.typesafe.config.Config
import xyz.nulldev.ts.config.ConfigModule
import xyz.nulldev.ts.config.GlobalConfigManager
import xyz.nulldev.ts.config.debugLogsEnabled

class ServerConfig(config: Config, moduleName: String = "") : ConfigModule(config, moduleName) {
    val ip: String by overridableWithSysProperty
    val port: Int by overridableWithSysProperty

    // proxy
    val socksProxyEnabled: Boolean by overridableWithSysProperty

    val socksProxyHost: String by overridableWithSysProperty
    val socksProxyPort: String by overridableWithSysProperty

    // misc
    val debugLogsEnabled: Boolean = debugLogsEnabled(GlobalConfigManager.config)
    val systemTrayEnabled: Boolean by overridableWithSysProperty

    // webUI
    val webUIEnabled: Boolean by overridableWithSysProperty
    val initialOpenInBrowserEnabled: Boolean by overridableWithSysProperty
    val webUIInterface: String by overridableWithSysProperty
    val electronPath: String by overridableWithSysProperty

    companion object {
        fun register(config: Config) = ServerConfig(config.getConfig("server"), "server")
    }
}
