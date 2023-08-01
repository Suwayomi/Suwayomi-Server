/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.WebUIChannel
import suwayomi.tachidesk.server.util.WebUIFlavor
import suwayomi.tachidesk.server.util.WebUIInterface

interface Settings {
    val ip: String?
    val port: Int?

    // proxy
    val socksProxyEnabled: Boolean?
    val socksProxyHost: String?
    val socksProxyPort: String?

    // webUI
//    requires restart (found no way to mutate (serve + "unserve") served files during runtime), exclude for now
//    val webUIEnabled: Boolean,
    val webUIFlavor: WebUIFlavor?
    val initialOpenInBrowserEnabled: Boolean?
    val webUIInterface: WebUIInterface?
    val electronPath: String?
    val webUIChannel: WebUIChannel?
    val webUIUpdateCheckInterval: Double?

    // downloader
    val downloadAsCbz: Boolean?
    val downloadsPath: String?
    val autoDownloadNewChapters: Boolean?

    // requests
    val maxSourcesInParallel: Int?

    // updater
    val excludeUnreadChapters: Boolean?
    val excludeNotStarted: Boolean?
    val excludeCompleted: Boolean?
    val globalUpdateInterval: Double?

    // Authentication
    val basicAuthEnabled: Boolean?
    val basicAuthUsername: String?
    val basicAuthPassword: String?

    // misc
    val debugLogsEnabled: Boolean?
    val systemTrayEnabled: Boolean?

    // backup
    val backupPath: String?
    val backupTime: String?
    val backupInterval: Int?
    val backupTTL: Int?

    // local source
    val localSourcePath: String?
}

data class PartialSettingsType(
    override val ip: String?,
    override val port: Int?,

    // proxy
    override val socksProxyEnabled: Boolean?,
    override val socksProxyHost: String?,
    override val socksProxyPort: String?,

    // webUI
    override val webUIFlavor: WebUIFlavor?,
    override val initialOpenInBrowserEnabled: Boolean?,
    override val webUIInterface: WebUIInterface?,
    override val electronPath: String?,
    override val webUIChannel: WebUIChannel?,
    override val webUIUpdateCheckInterval: Double?,

    // downloader
    override val downloadAsCbz: Boolean?,
    override val downloadsPath: String?,
    override val autoDownloadNewChapters: Boolean?,

    // requests
    override val maxSourcesInParallel: Int?,

    // updater
    override val excludeUnreadChapters: Boolean?,
    override val excludeNotStarted: Boolean?,
    override val excludeCompleted: Boolean?,
    override val globalUpdateInterval: Double?,

    // Authentication
    override val basicAuthEnabled: Boolean?,
    override val basicAuthUsername: String?,
    override val basicAuthPassword: String?,

    // misc
    override val debugLogsEnabled: Boolean?,
    override val systemTrayEnabled: Boolean?,

    // backup
    override val backupPath: String?,
    override val backupTime: String?,
    override val backupInterval: Int?,
    override val backupTTL: Int?,

    // local source
    override val localSourcePath: String?
) : Settings, Node

class SettingsType(
    override val ip: String,
    override val port: Int,

    // proxy
    override val socksProxyEnabled: Boolean,
    override val socksProxyHost: String,
    override val socksProxyPort: String,

    // webUI
    override val webUIFlavor: WebUIFlavor,
    override val initialOpenInBrowserEnabled: Boolean,
    override val webUIInterface: WebUIInterface,
    override val electronPath: String,
    override val webUIChannel: WebUIChannel,
    override val webUIUpdateCheckInterval: Double,

    // downloader
    override val downloadAsCbz: Boolean,
    override val downloadsPath: String,
    override val autoDownloadNewChapters: Boolean,

    // requests
    override val maxSourcesInParallel: Int,

    // updater
    override val excludeUnreadChapters: Boolean,
    override val excludeNotStarted: Boolean,
    override val excludeCompleted: Boolean,
    override val globalUpdateInterval: Double,

    // Authentication
    override val basicAuthEnabled: Boolean,
    override val basicAuthUsername: String,
    override val basicAuthPassword: String,

    // misc
    override val debugLogsEnabled: Boolean,
    override val systemTrayEnabled: Boolean,

    // backup
    override val backupPath: String,
    override val backupTime: String,
    override val backupInterval: Int,
    override val backupTTL: Int,

    // local source
    override val localSourcePath: String
) : Settings, Node {
    constructor() : this(
        serverConfig.ip.value,
        serverConfig.port.value,

        serverConfig.socksProxyEnabled.value,
        serverConfig.socksProxyHost.value,
        serverConfig.socksProxyPort.value,

        WebUIFlavor.from(serverConfig.webUIFlavor.value),
        serverConfig.initialOpenInBrowserEnabled.value,
        WebUIInterface.from(serverConfig.webUIInterface.value),
        serverConfig.electronPath.value,
        WebUIChannel.from(serverConfig.webUIChannel.value),
        serverConfig.webUIUpdateCheckInterval.value,

        serverConfig.downloadAsCbz.value,
        serverConfig.downloadsPath.value,
        serverConfig.autoDownloadNewChapters.value,

        serverConfig.maxSourcesInParallel.value,

        serverConfig.excludeUnreadChapters.value,
        serverConfig.excludeNotStarted.value,
        serverConfig.excludeCompleted.value,
        serverConfig.globalUpdateInterval.value,

        serverConfig.basicAuthEnabled.value,
        serverConfig.basicAuthUsername.value,
        serverConfig.basicAuthPassword.value,

        serverConfig.debugLogsEnabled.value,
        serverConfig.systemTrayEnabled.value,

        serverConfig.backupPath.value,
        serverConfig.backupTime.value,
        serverConfig.backupInterval.value,
        serverConfig.backupTTL.value,

        serverConfig.localSourcePath.value
    )
}
