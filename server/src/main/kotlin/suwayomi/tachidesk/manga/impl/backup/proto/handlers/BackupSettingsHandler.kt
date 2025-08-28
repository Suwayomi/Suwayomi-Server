@file:Suppress("ktlint")

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.manga.impl.backup.proto.handlers


import suwayomi.tachidesk.graphql.mutations.SettingsMutation
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupServerSettings
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.graphql.types.WebUIInterface
import suwayomi.tachidesk.graphql.types.WebUIChannel
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionType
import suwayomi.tachidesk.graphql.types.AuthMode
import kotlin.time.Duration
import org.jetbrains.exposed.sql.SortOrder
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncStrategy


object BackupSettingsHandler {
    fun backup(flags: BackupFlags): BackupServerSettings? {
        if (!flags.includeServerSettings) { return null }

        return BackupServerSettings(
            // Network
            ip = serverConfig.ip.value,
            port = serverConfig.port.value,
            // Proxy
            socksProxyEnabled = serverConfig.socksProxyEnabled.value,
            socksProxyVersion = serverConfig.socksProxyVersion.value,
            socksProxyHost = serverConfig.socksProxyHost.value,
            socksProxyPort = serverConfig.socksProxyPort.value,
            socksProxyUsername = serverConfig.socksProxyUsername.value,
            socksProxyPassword = serverConfig.socksProxyPassword.value,
            // WebUI
            webUIFlavor = serverConfig.webUIFlavor.value,
            initialOpenInBrowserEnabled = serverConfig.initialOpenInBrowserEnabled.value,
            webUIInterface = serverConfig.webUIInterface.value,
            electronPath = serverConfig.electronPath.value,
            webUIChannel = serverConfig.webUIChannel.value,
            webUIUpdateCheckInterval = serverConfig.webUIUpdateCheckInterval.value,
            webUIEnabled = serverConfig.webUIEnabled.value,
            // Downloader
            downloadAsCbz = serverConfig.downloadAsCbz.value,
            downloadsPath = serverConfig.downloadsPath.value,
            autoDownloadNewChapters = serverConfig.autoDownloadNewChapters.value,
            excludeEntryWithUnreadChapters = serverConfig.excludeEntryWithUnreadChapters.value,
            autoDownloadAheadLimit = serverConfig.autoDownloadAheadLimit.value,
            autoDownloadNewChaptersLimit = serverConfig.autoDownloadNewChaptersLimit.value,
            autoDownloadIgnoreReUploads = serverConfig.autoDownloadIgnoreReUploads.value,
            downloadConversions = SettingsRegistry.get("downloadConversions")!!.typeInfo.convertToBackupType!!(serverConfig.downloadConversions.value) as List<BackupSettingsDownloadConversionType>,
            // Extension/Source
            extensionRepos = serverConfig.extensionRepos.value,
            maxSourcesInParallel = serverConfig.maxSourcesInParallel.value,
            // Library updates
            excludeUnreadChapters = serverConfig.excludeUnreadChapters.value,
            excludeNotStarted = serverConfig.excludeNotStarted.value,
            excludeCompleted = serverConfig.excludeCompleted.value,
            globalUpdateInterval = serverConfig.globalUpdateInterval.value,
            updateMangas = serverConfig.updateMangas.value,
            test = serverConfig.test.value,
            // Authentication
            basicAuthEnabled = serverConfig.basicAuthEnabled.value,
            authUsername = serverConfig.authUsername.value,
            authPassword = serverConfig.authPassword.value,
            authMode = serverConfig.authMode.value,
            jwtAudience = serverConfig.jwtAudience.value,
            jwtTokenExpiry = serverConfig.jwtTokenExpiry.value,
            jwtRefreshExpiry = serverConfig.jwtRefreshExpiry.value,
            basicAuthUsername = serverConfig.basicAuthUsername.value,
            basicAuthPassword = serverConfig.basicAuthPassword.value,
            // Misc
            debugLogsEnabled = serverConfig.debugLogsEnabled.value,
            gqlDebugLogsEnabled = serverConfig.gqlDebugLogsEnabled.value,
            systemTrayEnabled = serverConfig.systemTrayEnabled.value,
            maxLogFiles = serverConfig.maxLogFiles.value,
            maxLogFileSize = serverConfig.maxLogFileSize.value,
            maxLogFolderSize = serverConfig.maxLogFolderSize.value,
            // Backup
            backupPath = serverConfig.backupPath.value,
            backupTime = serverConfig.backupTime.value,
            backupInterval = serverConfig.backupInterval.value,
            backupTTL = serverConfig.backupTTL.value,
            // Local source
            localSourcePath = serverConfig.localSourcePath.value,
            // Cloudflare
            flareSolverrEnabled = serverConfig.flareSolverrEnabled.value,
            flareSolverrUrl = serverConfig.flareSolverrUrl.value,
            flareSolverrTimeout = serverConfig.flareSolverrTimeout.value,
            flareSolverrSessionName = serverConfig.flareSolverrSessionName.value,
            flareSolverrSessionTtl = serverConfig.flareSolverrSessionTtl.value,
            flareSolverrAsResponseFallback = serverConfig.flareSolverrAsResponseFallback.value,
            // OPDS
            opdsUseBinaryFileSizes = serverConfig.opdsUseBinaryFileSizes.value,
            opdsItemsPerPage = serverConfig.opdsItemsPerPage.value,
            opdsEnablePageReadProgress = serverConfig.opdsEnablePageReadProgress.value,
            opdsMarkAsReadOnDownload = serverConfig.opdsMarkAsReadOnDownload.value,
            opdsShowOnlyUnreadChapters = serverConfig.opdsShowOnlyUnreadChapters.value,
            opdsShowOnlyDownloadedChapters = serverConfig.opdsShowOnlyDownloadedChapters.value,
            opdsChapterSortOrder = serverConfig.opdsChapterSortOrder.value,
            // KOReader sync
            koreaderSyncServerUrl = serverConfig.koreaderSyncServerUrl.value,
            koreaderSyncUsername = serverConfig.koreaderSyncUsername.value,
            koreaderSyncUserkey = serverConfig.koreaderSyncUserkey.value,
            koreaderSyncDeviceId = serverConfig.koreaderSyncDeviceId.value,
            koreaderSyncChecksumMethod = serverConfig.koreaderSyncChecksumMethod.value,
            koreaderSyncStrategy = serverConfig.koreaderSyncStrategy.value,
            koreaderSyncPercentageTolerance = serverConfig.koreaderSyncPercentageTolerance.value,
        )
    }

    fun restore(backupServerSettings: BackupServerSettings?) {
        if (backupServerSettings == null) { return }

        SettingsMutation().updateSettings(
            backupServerSettings.copy(
                basicAuthEnabled = SettingsRegistry.get("basicAuthEnabled")!!.typeInfo.restoreLegacy!!(backupServerSettings.basicAuthEnabled) as Boolean,
            ),
        )
    }
}

