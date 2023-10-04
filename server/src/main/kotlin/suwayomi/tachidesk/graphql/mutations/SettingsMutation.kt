package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.types.PartialSettingsType
import suwayomi.tachidesk.graphql.types.Settings
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.server.SERVER_CONFIG_MODULE_NAME
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.serverConfig
import xyz.nulldev.ts.config.GlobalConfigManager

class SettingsMutation {
    data class SetSettingsInput(
        val clientMutationId: String? = null,
        val settings: PartialSettingsType,
    )

    data class SetSettingsPayload(
        val clientMutationId: String?,
        val settings: SettingsType,
    )

    private fun updateSettings(settings: Settings) {
        if (settings.ip != null) serverConfig.ip.value = settings.ip!!
        if (settings.port != null) serverConfig.port.value = settings.port!!

        if (settings.socksProxyEnabled != null) serverConfig.socksProxyEnabled.value = settings.socksProxyEnabled!!
        if (settings.socksProxyHost != null) serverConfig.socksProxyHost.value = settings.socksProxyHost!!
        if (settings.socksProxyPort != null) serverConfig.socksProxyPort.value = settings.socksProxyPort!!

        if (settings.webUIFlavor != null) serverConfig.webUIFlavor.value = settings.webUIFlavor!!.uiName
        if (settings.initialOpenInBrowserEnabled != null) serverConfig.initialOpenInBrowserEnabled.value = settings.initialOpenInBrowserEnabled!!
        if (settings.webUIInterface != null) serverConfig.webUIInterface.value = settings.webUIInterface!!.name.lowercase()
        if (settings.electronPath != null) serverConfig.electronPath.value = settings.electronPath!!
        if (settings.webUIChannel != null) serverConfig.webUIChannel.value = settings.webUIChannel!!.name.lowercase()
        if (settings.webUIUpdateCheckInterval != null) serverConfig.webUIUpdateCheckInterval.value = settings.webUIUpdateCheckInterval!!

        if (settings.downloadAsCbz != null) serverConfig.downloadAsCbz.value = settings.downloadAsCbz!!
        if (settings.downloadsPath != null) serverConfig.downloadsPath.value = settings.downloadsPath!!
        if (settings.autoDownloadNewChapters != null) serverConfig.autoDownloadNewChapters.value = settings.autoDownloadNewChapters!!

        if (settings.maxSourcesInParallel != null) serverConfig.maxSourcesInParallel.value = settings.maxSourcesInParallel!!

        if (settings.excludeUnreadChapters != null) serverConfig.excludeUnreadChapters.value = settings.excludeUnreadChapters!!
        if (settings.excludeNotStarted != null) serverConfig.excludeNotStarted.value = settings.excludeNotStarted!!
        if (settings.excludeCompleted != null) serverConfig.excludeCompleted.value = settings.excludeCompleted!!
        if (settings.globalUpdateInterval != null) serverConfig.globalUpdateInterval.value = settings.globalUpdateInterval!!

        if (settings.basicAuthEnabled != null) serverConfig.basicAuthEnabled.value = settings.basicAuthEnabled!!
        if (settings.basicAuthUsername != null) serverConfig.basicAuthUsername.value = settings.basicAuthUsername!!
        if (settings.basicAuthPassword != null) serverConfig.basicAuthPassword.value = settings.basicAuthPassword!!

        if (settings.debugLogsEnabled != null) serverConfig.debugLogsEnabled.value = settings.debugLogsEnabled!!
        if (settings.systemTrayEnabled != null) serverConfig.systemTrayEnabled.value = settings.systemTrayEnabled!!

        if (settings.backupPath != null) serverConfig.backupPath.value = settings.backupPath!!
        if (settings.backupTime != null) serverConfig.backupTime.value = settings.backupTime!!
        if (settings.backupInterval != null) serverConfig.backupInterval.value = settings.backupInterval!!
        if (settings.backupTTL != null) serverConfig.backupTTL.value = settings.backupTTL!!

        if (settings.localSourcePath != null) serverConfig.localSourcePath.value = settings.localSourcePath!!
    }

    fun setSettings(input: SetSettingsInput): SetSettingsPayload {
        val (clientMutationId, settings) = input

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
