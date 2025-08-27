package suwayomi.tachidesk.manga.impl.backup.proto.handlers

import suwayomi.tachidesk.graphql.mutations.SettingsMutation
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupServerSettings
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupServerSettings.BackupSettingsDownloadConversionType
import suwayomi.tachidesk.server.serverConfig

object BackupSettingsHandler {
    fun backup(flags: BackupFlags): BackupServerSettings? {
        if (!flags.includeServerSettings) {
            return null
        }

        return BackupServerSettings(
            ip = serverConfig.ip.value,
            port = serverConfig.port.value,
            // socks
            socksProxyEnabled = serverConfig.socksProxyEnabled.value,
            socksProxyVersion = serverConfig.socksProxyVersion.value,
            socksProxyHost = serverConfig.socksProxyHost.value,
            socksProxyPort = serverConfig.socksProxyPort.value,
            socksProxyUsername = serverConfig.socksProxyUsername.value,
            socksProxyPassword = serverConfig.socksProxyPassword.value,
            // webUI
            webUIFlavor = serverConfig.webUIFlavor.value,
            initialOpenInBrowserEnabled = serverConfig.initialOpenInBrowserEnabled.value,
            webUIInterface = serverConfig.webUIInterface.value,
            electronPath = serverConfig.electronPath.value,
            webUIChannel = serverConfig.webUIChannel.value,
            webUIUpdateCheckInterval = serverConfig.webUIUpdateCheckInterval.value,
            // downloader
            downloadAsCbz = serverConfig.downloadAsCbz.value,
            downloadsPath = serverConfig.downloadsPath.value,
            autoDownloadNewChapters = serverConfig.autoDownloadNewChapters.value,
            excludeEntryWithUnreadChapters = serverConfig.excludeEntryWithUnreadChapters.value,
            autoDownloadAheadLimit = 0, // deprecated
            autoDownloadNewChaptersLimit = serverConfig.autoDownloadNewChaptersLimit.value,
            autoDownloadIgnoreReUploads = serverConfig.autoDownloadIgnoreReUploads.value,
            downloadConversions =
                serverConfig.downloadConversions.value.map {
                    BackupSettingsDownloadConversionType(
                        it.key,
                        it.value.target,
                        it.value.compressionLevel,
                    )
                },
            // extension
            extensionRepos = serverConfig.extensionRepos.value,
            // requests
            maxSourcesInParallel = serverConfig.maxSourcesInParallel.value,
            // updater
            excludeUnreadChapters = serverConfig.excludeUnreadChapters.value,
            excludeNotStarted = serverConfig.excludeNotStarted.value,
            excludeCompleted = serverConfig.excludeCompleted.value,
            globalUpdateInterval = serverConfig.globalUpdateInterval.value,
            updateMangas = serverConfig.updateMangas.value,
            // Authentication
            authMode = serverConfig.authMode.value,
            jwtAudience = serverConfig.jwtAudience.value,
            jwtTokenExpiry = serverConfig.jwtTokenExpiry.value,
            jwtRefreshExpiry = serverConfig.jwtRefreshExpiry.value,
            authUsername = serverConfig.authUsername.value,
            authPassword = serverConfig.authPassword.value,
            basicAuthEnabled = false,
            basicAuthUsername = null,
            basicAuthPassword = null,
            // misc
            debugLogsEnabled = serverConfig.debugLogsEnabled.value,
            gqlDebugLogsEnabled = false, // deprecated
            systemTrayEnabled = serverConfig.systemTrayEnabled.value,
            maxLogFiles = serverConfig.maxLogFiles.value,
            maxLogFileSize = serverConfig.maxLogFileSize.value,
            maxLogFolderSize = serverConfig.maxLogFolderSize.value,
            // backup
            backupPath = serverConfig.backupPath.value,
            backupTime = serverConfig.backupTime.value,
            backupInterval = serverConfig.backupInterval.value,
            backupTTL = serverConfig.backupTTL.value,
            // local source
            localSourcePath = serverConfig.localSourcePath.value,
            // cloudflare bypass
            flareSolverrEnabled = serverConfig.flareSolverrEnabled.value,
            flareSolverrUrl = serverConfig.flareSolverrUrl.value,
            flareSolverrTimeout = serverConfig.flareSolverrTimeout.value,
            flareSolverrSessionName = serverConfig.flareSolverrSessionName.value,
            flareSolverrSessionTtl = serverConfig.flareSolverrSessionTtl.value,
            flareSolverrAsResponseFallback = serverConfig.flareSolverrAsResponseFallback.value,
            // opds
            opdsUseBinaryFileSizes = serverConfig.opdsUseBinaryFileSizes.value,
            opdsItemsPerPage = serverConfig.opdsItemsPerPage.value,
            opdsEnablePageReadProgress = serverConfig.opdsEnablePageReadProgress.value,
            opdsMarkAsReadOnDownload = serverConfig.opdsMarkAsReadOnDownload.value,
            opdsShowOnlyUnreadChapters = serverConfig.opdsShowOnlyUnreadChapters.value,
            opdsShowOnlyDownloadedChapters = serverConfig.opdsShowOnlyDownloadedChapters.value,
            opdsChapterSortOrder = serverConfig.opdsChapterSortOrder.value,
            // koreader sync
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
        if (backupServerSettings == null) {
            return
        }

        SettingsMutation().updateSettings(
            backupServerSettings.copy(
                // legacy settings cannot overwrite new settings
                basicAuthEnabled =
                    backupServerSettings.basicAuthEnabled.takeIf {
                        serverConfig.authMode.value == AuthMode.NONE
                    },
            ),
        )
    }
}
