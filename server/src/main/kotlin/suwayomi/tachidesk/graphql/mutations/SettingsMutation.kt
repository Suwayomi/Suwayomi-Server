package suwayomi.tachidesk.graphql.mutations

import kotlinx.coroutines.flow.MutableStateFlow
import suwayomi.tachidesk.graphql.types.PartialSettingsType
import suwayomi.tachidesk.graphql.types.Settings
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.server.SERVER_CONFIG_MODULE_NAME
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.serverConfig
import xyz.nulldev.ts.config.GlobalConfigManager

private fun validateString(
    value: String?,
    pattern: Regex,
    name: String,
) {
    validateString(value, pattern, Exception("Invalid format for \"$name\" [$value]"))
}

private fun validateString(
    value: String?,
    pattern: Regex,
    exception: Exception,
) {
    if (value != null && !value.matches(pattern)) {
        throw exception
    }
}

class SettingsMutation {
    data class SetSettingsInput(
        val clientMutationId: String? = null,
        val settings: PartialSettingsType,
    )

    data class SetSettingsPayload(
        val clientMutationId: String?,
        val settings: SettingsType,
    )

    private fun validateSettings(settings: Settings) {
        // misc
        val logbackSizePattern = "^[0-9]+(|kb|KB|mb|MB|gb|GB)\$".toRegex()
        validateString(settings.maxLogFileSize, logbackSizePattern, "maxLogFileSize")
        validateString(settings.maxLogFolderSize, logbackSizePattern, "maxLogFolderSize")
    }

    private fun <SettingType : Any> updateSetting(
        newSetting: SettingType?,
        configSetting: MutableStateFlow<SettingType>,
    ) {
        if (newSetting == null) {
            return
        }

        configSetting.value = newSetting
    }

    private fun updateSettings(settings: Settings) {
        updateSetting(settings.ip, serverConfig.ip)
        updateSetting(settings.port, serverConfig.port)

        // proxy
        updateSetting(settings.socksProxyEnabled, serverConfig.socksProxyEnabled)
        updateSetting(settings.socksProxyVersion, serverConfig.socksProxyVersion)
        updateSetting(settings.socksProxyHost, serverConfig.socksProxyHost)
        updateSetting(settings.socksProxyPort, serverConfig.socksProxyPort)
        updateSetting(settings.socksProxyUsername, serverConfig.socksProxyUsername)
        updateSetting(settings.socksProxyPassword, serverConfig.socksProxyPassword)

        // webUI
        updateSetting(settings.webUIFlavor?.uiName, serverConfig.webUIFlavor)
        updateSetting(settings.initialOpenInBrowserEnabled, serverConfig.initialOpenInBrowserEnabled)
        updateSetting(settings.webUIInterface?.name?.lowercase(), serverConfig.webUIInterface)
        updateSetting(settings.electronPath, serverConfig.electronPath)
        updateSetting(settings.webUIChannel?.name?.lowercase(), serverConfig.webUIChannel)
        updateSetting(settings.webUIUpdateCheckInterval, serverConfig.webUIUpdateCheckInterval)

        // downloader
        updateSetting(settings.downloadAsCbz, serverConfig.downloadAsCbz)
        updateSetting(settings.downloadsPath, serverConfig.downloadsPath)
        updateSetting(settings.autoDownloadNewChapters, serverConfig.autoDownloadNewChapters)
        updateSetting(settings.excludeEntryWithUnreadChapters, serverConfig.excludeEntryWithUnreadChapters)
        updateSetting(settings.autoDownloadAheadLimit, serverConfig.autoDownloadNewChaptersLimit) // deprecated
        updateSetting(settings.autoDownloadNewChaptersLimit, serverConfig.autoDownloadNewChaptersLimit)
        updateSetting(settings.autoDownloadIgnoreReUploads, serverConfig.autoDownloadIgnoreReUploads)

        // extension
        updateSetting(settings.extensionRepos, serverConfig.extensionRepos)

        // requests
        updateSetting(settings.maxSourcesInParallel, serverConfig.maxSourcesInParallel)

        // updater
        updateSetting(settings.excludeUnreadChapters, serverConfig.excludeUnreadChapters)
        updateSetting(settings.excludeNotStarted, serverConfig.excludeNotStarted)
        updateSetting(settings.excludeCompleted, serverConfig.excludeCompleted)
        updateSetting(settings.globalUpdateInterval, serverConfig.globalUpdateInterval)
        updateSetting(settings.updateMangas, serverConfig.updateMangas)

        // Authentication
        updateSetting(settings.basicAuthEnabled, serverConfig.basicAuthEnabled)
        updateSetting(settings.basicAuthUsername, serverConfig.basicAuthUsername)
        updateSetting(settings.basicAuthPassword, serverConfig.basicAuthPassword)

        // misc
        updateSetting(settings.debugLogsEnabled, serverConfig.debugLogsEnabled)
        updateSetting(settings.gqlDebugLogsEnabled, serverConfig.gqlDebugLogsEnabled)
        updateSetting(settings.systemTrayEnabled, serverConfig.systemTrayEnabled)
        updateSetting(settings.maxLogFiles, serverConfig.maxLogFiles)
        updateSetting(settings.maxLogFileSize, serverConfig.maxLogFileSize)
        updateSetting(settings.maxLogFolderSize, serverConfig.maxLogFolderSize)

        // backup
        updateSetting(settings.backupPath, serverConfig.backupPath)
        updateSetting(settings.backupTime, serverConfig.backupTime)
        updateSetting(settings.backupInterval, serverConfig.backupInterval)
        updateSetting(settings.backupTTL, serverConfig.backupTTL)

        // local source
        updateSetting(settings.localSourcePath, serverConfig.localSourcePath)

        // cloudflare bypass
        updateSetting(settings.flareSolverrEnabled, serverConfig.flareSolverrEnabled)
        updateSetting(settings.flareSolverrUrl, serverConfig.flareSolverrUrl)
        updateSetting(settings.flareSolverrTimeout, serverConfig.flareSolverrTimeout)
        updateSetting(settings.flareSolverrSessionName, serverConfig.flareSolverrSessionName)
        updateSetting(settings.flareSolverrSessionTtl, serverConfig.flareSolverrSessionTtl)
        updateSetting(settings.flareSolverrAsResponseFallback, serverConfig.flareSolverrAsResponseFallback)
    }

    fun setSettings(input: SetSettingsInput): SetSettingsPayload {
        val (clientMutationId, settings) = input

        validateSettings(settings)
        updateSettings(settings)

        return SetSettingsPayload(clientMutationId, SettingsType())
    }

    data class ResetSettingsInput(val clientMutationId: String? = null)

    data class ResetSettingsPayload(
        val clientMutationId: String?,
        val settings: SettingsType,
    )

    fun resetSettings(input: ResetSettingsInput): ResetSettingsPayload {
        val (clientMutationId) = input

        GlobalConfigManager.resetUserConfig()
        val defaultServerConfig = ServerConfig({ GlobalConfigManager.config.getConfig(SERVER_CONFIG_MODULE_NAME) })

        val settings = SettingsType(defaultServerConfig)
        updateSettings(settings)

        return ResetSettingsPayload(clientMutationId, settings)
    }
}
