@file:Suppress("ktlint")

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import org.jetbrains.exposed.sql.SortOrder
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversion
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionType
import kotlin.time.Duration

interface Settings : Node {
    // Network
    val ip: String?
    val port: Int?
    // Proxy
    val socksProxyEnabled: Boolean?
    val socksProxyVersion: Int?
    val socksProxyHost: String?
    val socksProxyPort: String?
    val socksProxyUsername: String?
    val socksProxyPassword: String?
    // WebUI
    val webUIFlavor: WebUIFlavor?
    val initialOpenInBrowserEnabled: Boolean?
    val webUIInterface: WebUIInterface?
    val electronPath: String?
    val webUIChannel: WebUIChannel?
    val webUIUpdateCheckInterval: Double?
    // Downloader
    val downloadAsCbz: Boolean?
    val downloadsPath: String?
    val autoDownloadNewChapters: Boolean?
    val excludeEntryWithUnreadChapters: Boolean?
    val autoDownloadNewChaptersLimit: Int?
    val autoDownloadIgnoreReUploads: Boolean?
    val downloadConversions: List<SettingsDownloadConversion>?
    @GraphQLDeprecated("Replaced with autoDownloadNewChaptersLimit", ReplaceWith("autoDownloadNewChaptersLimit"))
    val autoDownloadAheadLimit: Int?
    // Extension/Source
    val extensionRepos: List<String>?
    val maxSourcesInParallel: Int?
    // Library updates
    val excludeUnreadChapters: Boolean?
    val excludeNotStarted: Boolean?
    val excludeCompleted: Boolean?
    val globalUpdateInterval: Double?
    val updateMangas: Boolean?
    // Authentication
    val authMode: AuthMode?
    val authUsername: String?
    val authPassword: String?
    val jwtAudience: String?
    val jwtTokenExpiry: Duration?
    val jwtRefreshExpiry: Duration?
    @GraphQLDeprecated("Removed - prefer authUsername", ReplaceWith("authMode"))
    val basicAuthEnabled: Boolean?
    @GraphQLDeprecated("Removed - prefer authUsername", ReplaceWith("authUsername"))
    val basicAuthUsername: String?
    @GraphQLDeprecated("Removed - prefer authPassword", ReplaceWith("authPassword"))
    val basicAuthPassword: String?
    // Misc
    val debugLogsEnabled: Boolean?
    val systemTrayEnabled: Boolean?
    val maxLogFiles: Int?
    val maxLogFileSize: String?
    val maxLogFolderSize: String?
    @GraphQLDeprecated("Removed - does not do anything")
    val gqlDebugLogsEnabled: Boolean?
    // Backup
    val backupPath: String?
    val backupTime: String?
    val backupInterval: Int?
    val backupTTL: Int?
    // Local source
    val localSourcePath: String?
    // Cloudflare
    val flareSolverrEnabled: Boolean?
    val flareSolverrUrl: String?
    val flareSolverrTimeout: Int?
    val flareSolverrSessionName: String?
    val flareSolverrSessionTtl: Int?
    val flareSolverrAsResponseFallback: Boolean?
    // OPDS
    val opdsUseBinaryFileSizes: Boolean?
    val opdsItemsPerPage: Int?
    val opdsEnablePageReadProgress: Boolean?
    val opdsMarkAsReadOnDownload: Boolean?
    val opdsShowOnlyUnreadChapters: Boolean?
    val opdsShowOnlyDownloadedChapters: Boolean?
    val opdsChapterSortOrder: SortOrder?
    // KOReader sync
    val koreaderSyncServerUrl: String?
    val koreaderSyncUsername: String?
    val koreaderSyncUserkey: String?
    val koreaderSyncDeviceId: String?
    val koreaderSyncChecksumMethod: KoreaderSyncChecksumMethod?
    val koreaderSyncStrategy: KoreaderSyncStrategy?
    val koreaderSyncPercentageTolerance: Double?
}

data class PartialSettingsType(
    // Network
    override val ip: String?,
    override val port: Int?,
    // Proxy
    override val socksProxyEnabled: Boolean?,
    override val socksProxyVersion: Int?,
    override val socksProxyHost: String?,
    override val socksProxyPort: String?,
    override val socksProxyUsername: String?,
    override val socksProxyPassword: String?,
    // WebUI
    override val webUIFlavor: WebUIFlavor?,
    override val initialOpenInBrowserEnabled: Boolean?,
    override val webUIInterface: WebUIInterface?,
    override val electronPath: String?,
    override val webUIChannel: WebUIChannel?,
    override val webUIUpdateCheckInterval: Double?,
    // Downloader
    override val downloadAsCbz: Boolean?,
    override val downloadsPath: String?,
    override val autoDownloadNewChapters: Boolean?,
    override val excludeEntryWithUnreadChapters: Boolean?,
    override val autoDownloadNewChaptersLimit: Int?,
    override val autoDownloadIgnoreReUploads: Boolean?,
    override val downloadConversions: List<SettingsDownloadConversionType>?,
    @GraphQLDeprecated("Replaced with autoDownloadNewChaptersLimit", ReplaceWith("autoDownloadNewChaptersLimit"))
    override val autoDownloadAheadLimit: Int?,
    // Extension/Source
    override val extensionRepos: List<String>?,
    override val maxSourcesInParallel: Int?,
    // Library updates
    override val excludeUnreadChapters: Boolean?,
    override val excludeNotStarted: Boolean?,
    override val excludeCompleted: Boolean?,
    override val globalUpdateInterval: Double?,
    override val updateMangas: Boolean?,
    // Authentication
    override val authMode: AuthMode?,
    override val authUsername: String?,
    override val authPassword: String?,
    override val jwtAudience: String?,
    override val jwtTokenExpiry: Duration?,
    override val jwtRefreshExpiry: Duration?,
    @GraphQLDeprecated("Removed - prefer authUsername", ReplaceWith("authMode"))
    override val basicAuthEnabled: Boolean?,
    @GraphQLDeprecated("Removed - prefer authUsername", ReplaceWith("authUsername"))
    override val basicAuthUsername: String?,
    @GraphQLDeprecated("Removed - prefer authPassword", ReplaceWith("authPassword"))
    override val basicAuthPassword: String?,
    // Misc
    override val debugLogsEnabled: Boolean?,
    override val systemTrayEnabled: Boolean?,
    override val maxLogFiles: Int?,
    override val maxLogFileSize: String?,
    override val maxLogFolderSize: String?,
    @GraphQLDeprecated("Removed - does not do anything")
    override val gqlDebugLogsEnabled: Boolean?,
    // Backup
    override val backupPath: String?,
    override val backupTime: String?,
    override val backupInterval: Int?,
    override val backupTTL: Int?,
    // Local source
    override val localSourcePath: String?,
    // Cloudflare
    override val flareSolverrEnabled: Boolean?,
    override val flareSolverrUrl: String?,
    override val flareSolverrTimeout: Int?,
    override val flareSolverrSessionName: String?,
    override val flareSolverrSessionTtl: Int?,
    override val flareSolverrAsResponseFallback: Boolean?,
    // OPDS
    override val opdsUseBinaryFileSizes: Boolean?,
    override val opdsItemsPerPage: Int?,
    override val opdsEnablePageReadProgress: Boolean?,
    override val opdsMarkAsReadOnDownload: Boolean?,
    override val opdsShowOnlyUnreadChapters: Boolean?,
    override val opdsShowOnlyDownloadedChapters: Boolean?,
    override val opdsChapterSortOrder: SortOrder?,
    // KOReader sync
    override val koreaderSyncServerUrl: String?,
    override val koreaderSyncUsername: String?,
    override val koreaderSyncUserkey: String?,
    override val koreaderSyncDeviceId: String?,
    override val koreaderSyncChecksumMethod: KoreaderSyncChecksumMethod?,
    override val koreaderSyncStrategy: KoreaderSyncStrategy?,
    override val koreaderSyncPercentageTolerance: Double?,
) : Settings

class SettingsType(
    // Network
    override val ip: String,
    override val port: Int,
    // Proxy
    override val socksProxyEnabled: Boolean,
    override val socksProxyVersion: Int,
    override val socksProxyHost: String,
    override val socksProxyPort: String,
    override val socksProxyUsername: String,
    override val socksProxyPassword: String,
    // WebUI
    override val webUIFlavor: WebUIFlavor,
    override val initialOpenInBrowserEnabled: Boolean,
    override val webUIInterface: WebUIInterface,
    override val electronPath: String,
    override val webUIChannel: WebUIChannel,
    override val webUIUpdateCheckInterval: Double,
    // Downloader
    override val downloadAsCbz: Boolean,
    override val downloadsPath: String,
    override val autoDownloadNewChapters: Boolean,
    override val excludeEntryWithUnreadChapters: Boolean,
    override val autoDownloadNewChaptersLimit: Int,
    override val autoDownloadIgnoreReUploads: Boolean,
    override val downloadConversions: List<SettingsDownloadConversionType>,
    @GraphQLDeprecated("Replaced with autoDownloadNewChaptersLimit", ReplaceWith("autoDownloadNewChaptersLimit"))
    override val autoDownloadAheadLimit: Int,
    // Extension/Source
    override val extensionRepos: List<String>,
    override val maxSourcesInParallel: Int,
    // Library updates
    override val excludeUnreadChapters: Boolean,
    override val excludeNotStarted: Boolean,
    override val excludeCompleted: Boolean,
    override val globalUpdateInterval: Double,
    override val updateMangas: Boolean,
    // Authentication
    override val authMode: AuthMode,
    override val authUsername: String,
    override val authPassword: String,
    override val jwtAudience: String,
    override val jwtTokenExpiry: Duration,
    override val jwtRefreshExpiry: Duration,
    @GraphQLDeprecated("Removed - prefer authUsername", ReplaceWith("authMode"))
    override val basicAuthEnabled: Boolean,
    @GraphQLDeprecated("Removed - prefer authUsername", ReplaceWith("authUsername"))
    override val basicAuthUsername: String,
    @GraphQLDeprecated("Removed - prefer authPassword", ReplaceWith("authPassword"))
    override val basicAuthPassword: String,
    // Misc
    override val debugLogsEnabled: Boolean,
    override val systemTrayEnabled: Boolean,
    override val maxLogFiles: Int,
    override val maxLogFileSize: String,
    override val maxLogFolderSize: String,
    @GraphQLDeprecated("Removed - does not do anything")
    override val gqlDebugLogsEnabled: Boolean,
    // Backup
    override val backupPath: String,
    override val backupTime: String,
    override val backupInterval: Int,
    override val backupTTL: Int,
    // Local source
    override val localSourcePath: String,
    // Cloudflare
    override val flareSolverrEnabled: Boolean,
    override val flareSolverrUrl: String,
    override val flareSolverrTimeout: Int,
    override val flareSolverrSessionName: String,
    override val flareSolverrSessionTtl: Int,
    override val flareSolverrAsResponseFallback: Boolean,
    // OPDS
    override val opdsUseBinaryFileSizes: Boolean,
    override val opdsItemsPerPage: Int,
    override val opdsEnablePageReadProgress: Boolean,
    override val opdsMarkAsReadOnDownload: Boolean,
    override val opdsShowOnlyUnreadChapters: Boolean,
    override val opdsShowOnlyDownloadedChapters: Boolean,
    override val opdsChapterSortOrder: SortOrder,
    // KOReader sync
    override val koreaderSyncServerUrl: String,
    override val koreaderSyncUsername: String,
    override val koreaderSyncUserkey: String,
    override val koreaderSyncDeviceId: String,
    override val koreaderSyncChecksumMethod: KoreaderSyncChecksumMethod,
    override val koreaderSyncStrategy: KoreaderSyncStrategy,
    override val koreaderSyncPercentageTolerance: Double,
) : Settings {
    @Suppress("UNCHECKED_CAST")
    constructor(config: ServerConfig = serverConfig) : this(
        // Network
        config.ip.value,
        config.port.value,
        // Proxy
        config.socksProxyEnabled.value,
        config.socksProxyVersion.value,
        config.socksProxyHost.value,
        config.socksProxyPort.value,
        config.socksProxyUsername.value,
        config.socksProxyPassword.value,
        // WebUI
        config.webUIFlavor.value,
        config.initialOpenInBrowserEnabled.value,
        config.webUIInterface.value,
        config.electronPath.value,
        config.webUIChannel.value,
        config.webUIUpdateCheckInterval.value,
        // Downloader
        config.downloadAsCbz.value,
        config.downloadsPath.value,
        config.autoDownloadNewChapters.value,
        config.excludeEntryWithUnreadChapters.value,
        config.autoDownloadNewChaptersLimit.value,
        config.autoDownloadIgnoreReUploads.value,
        SettingsRegistry.get("downloadConversions")!!.typeInfo.convertToGqlType!!(config.downloadConversions.value) as List<SettingsDownloadConversionType>,
        config.autoDownloadAheadLimit.value,
        // Extension/Source
        config.extensionRepos.value,
        config.maxSourcesInParallel.value,
        // Library updates
        config.excludeUnreadChapters.value,
        config.excludeNotStarted.value,
        config.excludeCompleted.value,
        config.globalUpdateInterval.value,
        config.updateMangas.value,
        // Authentication
        config.authMode.value,
        config.authUsername.value,
        config.authPassword.value,
        config.jwtAudience.value,
        config.jwtTokenExpiry.value,
        config.jwtRefreshExpiry.value,
        config.basicAuthEnabled.value,
        config.basicAuthUsername.value,
        config.basicAuthPassword.value,
        // Misc
        config.debugLogsEnabled.value,
        config.systemTrayEnabled.value,
        config.maxLogFiles.value,
        config.maxLogFileSize.value,
        config.maxLogFolderSize.value,
        config.gqlDebugLogsEnabled.value,
        // Backup
        config.backupPath.value,
        config.backupTime.value,
        config.backupInterval.value,
        config.backupTTL.value,
        // Local source
        config.localSourcePath.value,
        // Cloudflare
        config.flareSolverrEnabled.value,
        config.flareSolverrUrl.value,
        config.flareSolverrTimeout.value,
        config.flareSolverrSessionName.value,
        config.flareSolverrSessionTtl.value,
        config.flareSolverrAsResponseFallback.value,
        // OPDS
        config.opdsUseBinaryFileSizes.value,
        config.opdsItemsPerPage.value,
        config.opdsEnablePageReadProgress.value,
        config.opdsMarkAsReadOnDownload.value,
        config.opdsShowOnlyUnreadChapters.value,
        config.opdsShowOnlyDownloadedChapters.value,
        config.opdsChapterSortOrder.value,
        // KOReader sync
        config.koreaderSyncServerUrl.value,
        config.koreaderSyncUsername.value,
        config.koreaderSyncUserkey.value,
        config.koreaderSyncDeviceId.value,
        config.koreaderSyncChecksumMethod.value,
        config.koreaderSyncStrategy.value,
        config.koreaderSyncPercentageTolerance.value,
    )
}

