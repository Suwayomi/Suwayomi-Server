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
class ServerConfig(getConfig: () -> Config, moduleName: String = MODULE_NAME) : SystemPropertyOverridableConfigModule(getConfig, moduleName) {
    var ip: String by overridableConfig
    var port: Int by overridableConfig

    // proxy
    var socksProxyEnabled: Boolean by overridableConfig
    var socksProxyHost: String by overridableConfig
    var socksProxyPort: String by overridableConfig

    // webUI
    var webUIEnabled: Boolean by overridableConfig
    var webUIFlavor: String by overridableConfig
    var initialOpenInBrowserEnabled: Boolean by overridableConfig
    var webUIInterface: String by overridableConfig
    var electronPath: String by overridableConfig
    var webUIChannel: String by overridableConfig
    var webUIAutoUpdate: Boolean by overridableConfig

    // downloader
    var downloadAsCbz: Boolean by overridableConfig
    var downloadsPath: String by overridableConfig
    var autoDownloadNewChapters: Boolean by overridableConfig

    // updater
    var maxParallelUpdateRequests: Int by overridableConfig
    var excludeUnreadChapters: Boolean by overridableConfig
    var excludeNotStarted: Boolean by overridableConfig
    var excludeCompleted: Boolean by overridableConfig
    var automaticallyTriggerGlobalUpdate: Boolean by overridableConfig
    var globalUpdateInterval: Double by overridableConfig

    // Authentication
    var basicAuthEnabled: Boolean by overridableConfig
    var basicAuthUsername: String by overridableConfig
    var basicAuthPassword: String by overridableConfig

    // misc
    var debugLogsEnabled: Boolean = debugLogsEnabled(GlobalConfigManager.config)
    var systemTrayEnabled: Boolean by overridableConfig

    // backup
    var backupPath: String by overridableConfig
    var backupTime: String by overridableConfig
    var backupInterval: Int by overridableConfig
    var automatedBackups: Boolean by overridableConfig
    var backupTTL: Int by overridableConfig

    companion object {
        fun register(getConfig: () -> Config) = ServerConfig({ getConfig().getConfig(MODULE_NAME) })
    }
}
