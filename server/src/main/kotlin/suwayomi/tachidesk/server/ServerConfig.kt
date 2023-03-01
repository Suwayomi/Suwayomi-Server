package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.typesafe.config.Config
import xyz.nulldev.ts.config.GlobalConfigManager
import xyz.nulldev.ts.config.SystemPropertyOverridableConfigModule
import xyz.nulldev.ts.config.debugLogsEnabled

private const val MODULE_NAME = "server"
class ServerConfig(config: Config, moduleName: String = MODULE_NAME) : SystemPropertyOverridableConfigModule(config, moduleName) {
    val ip: String by overridableConfig
    val port: Int by overridableConfig

    // proxy
    val socksProxyEnabled: Boolean by overridableConfig
    val socksProxyHost: String by overridableConfig
    val socksProxyPort: String by overridableConfig

    // webUI
    val webUIEnabled: Boolean by overridableConfig
    val webUIFlavor: String by overridableConfig
    val initialOpenInBrowserEnabled: Boolean by overridableConfig
    val webUIInterface: String by overridableConfig
    val electronPath: String by overridableConfig

    // downloader
    val downloadAsCbz: Boolean by overridableConfig
    val downloadsPath: String by overridableConfig

    // updater
    val maxParallelUpdateRequests: Int by overridableConfig

    // Authentication
    val basicAuthEnabled: Boolean by overridableConfig
    val basicAuthUsername: String by overridableConfig
    val basicAuthPassword: String by overridableConfig

    // misc
    val debugLogsEnabled: Boolean = debugLogsEnabled(GlobalConfigManager.config)
    val systemTrayEnabled: Boolean by overridableConfig

    companion object {
        fun register(config: Config) = ServerConfig(config.getConfig(MODULE_NAME))
    }
}
