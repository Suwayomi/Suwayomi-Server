package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.typesafe.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import org.jetbrains.exposed.sql.SortOrder
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncStrategy
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionType
import suwayomi.tachidesk.graphql.types.WebUIChannel
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.graphql.types.WebUIInterface
import suwayomi.tachidesk.manga.impl.extension.ExtensionsList.repoMatchRegex
import suwayomi.tachidesk.server.settings.BooleanSetting
import suwayomi.tachidesk.server.settings.DisableableDoubleSetting
import suwayomi.tachidesk.server.settings.DisableableIntSetting
import suwayomi.tachidesk.server.settings.DoubleSetting
import suwayomi.tachidesk.server.settings.DurationSetting
import suwayomi.tachidesk.server.settings.EnumSetting
import suwayomi.tachidesk.server.settings.IntSetting
import suwayomi.tachidesk.server.settings.ListSetting
import suwayomi.tachidesk.server.settings.MapSetting
import suwayomi.tachidesk.server.settings.MigratedConfigValue
import suwayomi.tachidesk.server.settings.PathSetting
import suwayomi.tachidesk.server.settings.StringSetting
import xyz.nulldev.ts.config.SystemPropertyOverridableConfigModule
import kotlin.collections.associate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val mutableConfigValueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

const val SERVER_CONFIG_MODULE_NAME = "server"

class ServerConfig(
    getConfig: () -> Config,
) : SystemPropertyOverridableConfigModule(
        getConfig,
        SERVER_CONFIG_MODULE_NAME,
    ) {
    // proxy
    val ip: MutableStateFlow<String> by StringSetting(
        defaultValue = "0.0.0.0",
        pattern = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$".toRegex(),
    )
    val port: MutableStateFlow<Int> by IntSetting(defaultValue = 4567, min = 1, max = 65535)

    // webUI
    val socksProxyEnabled: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)
    val socksProxyVersion: MutableStateFlow<Int> by IntSetting(defaultValue = 5, min = 4, max = 5)
    val socksProxyHost: MutableStateFlow<String> by StringSetting(defaultValue = "")
    val socksProxyPort: MutableStateFlow<String> by StringSetting(defaultValue = "")
    val socksProxyUsername: MutableStateFlow<String> by StringSetting(defaultValue = "")
    val socksProxyPassword: MutableStateFlow<String> by StringSetting(defaultValue = "")

    // downloader
    val webUIEnabled: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = true)
    val webUIFlavor: MutableStateFlow<WebUIFlavor> by EnumSetting(defaultValue = WebUIFlavor.WEBUI)
    val initialOpenInBrowserEnabled: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = true)
    val webUIInterface: MutableStateFlow<WebUIInterface> by EnumSetting(defaultValue = WebUIInterface.BROWSER)
    val electronPath: MutableStateFlow<String> by PathSetting(defaultValue = "", mustExist = true)
    val webUIChannel: MutableStateFlow<WebUIChannel> by EnumSetting(defaultValue = WebUIChannel.STABLE)
    val webUIUpdateCheckInterval: MutableStateFlow<Double> by DisableableDoubleSetting(
        defaultValue = 23.hours.inWholeHours.toDouble(),
        min = 0.0,
        max = 23.0,
    )

    // extensions
    val downloadAsCbz: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)
    val downloadsPath: MutableStateFlow<String> by PathSetting(defaultValue = "", mustExist = true)
    val autoDownloadNewChapters: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)
    val excludeEntryWithUnreadChapters: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = true)
    val autoDownloadNewChaptersLimit: MutableStateFlow<Int> by DisableableIntSetting(defaultValue = 0, min = 0)
    val autoDownloadIgnoreReUploads: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)

    data class DownloadConversion(
        val target: String,
        val compressionLevel: Double? = null,
    )

    val downloadConversions: MutableStateFlow<Map<String, DownloadConversion>> by MapSetting<String, DownloadConversion>(
        defaultValue = emptyMap(),
        convertGqlToInternalType = { list ->
            @Suppress("UNCHECKED_CAST")
            val castedList = list as List<SettingsDownloadConversionType>

            castedList.associate {
                it.mimeType to
                    DownloadConversion(
                        target = it.target,
                        compressionLevel = it.compressionLevel,
                    )
            }
        },
    )

    // requests
    val extensionRepos: MutableStateFlow<List<String>> by ListSetting<String>(
        defaultValue = emptyList(),
        itemValidator = { url ->
            if (url.matches(repoMatchRegex)) {
                null
            } else {
                "Invalid repository URL format"
            }
        },
    )
    val maxSourcesInParallel: MutableStateFlow<Int> by IntSetting(defaultValue = 6, min = 1, max = 20)

    // updater
    val excludeUnreadChapters: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = true)
    val excludeNotStarted: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = true)
    val excludeCompleted: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = true)
    val globalUpdateInterval: MutableStateFlow<Double> by DisableableDoubleSetting(
        defaultValue = 12.hours.inWholeHours.toDouble(),
        min = 6.0,
    )
    val updateMangas: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)

    // Authentication
    val authMode: MutableStateFlow<AuthMode> by EnumSetting(defaultValue = AuthMode.NONE)
    val authUsername: MutableStateFlow<String> by StringSetting(defaultValue = "")
    val authPassword: MutableStateFlow<String> by StringSetting(defaultValue = "")
    val jwtAudience: MutableStateFlow<String> by StringSetting(defaultValue = "suwayomi-server-api")
    val jwtTokenExpiry: MutableStateFlow<Duration> by DurationSetting(defaultValue = 5.minutes, min = 0.seconds)
    val jwtRefreshExpiry: MutableStateFlow<Duration> by DurationSetting(defaultValue = 60.days, min = 0.seconds)

    // misc
    val debugLogsEnabled: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)
    val systemTrayEnabled: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = true)
    val maxLogFiles: MutableStateFlow<Int> by IntSetting(defaultValue = 31, min = 0)

    private val logbackSizePattern = "^[0-9]+(|kb|KB|mb|MB|gb|GB)$".toRegex()
    val maxLogFileSize: MutableStateFlow<String> by StringSetting(defaultValue = "10mb", pattern = logbackSizePattern)
    val maxLogFolderSize: MutableStateFlow<String> by StringSetting(defaultValue = "100mb", pattern = logbackSizePattern)

    // backup
    val backupPath: MutableStateFlow<String> by PathSetting(defaultValue = "", mustExist = true)
    val backupTime: MutableStateFlow<String> by StringSetting(
        defaultValue = "00:00",
        pattern = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$".toRegex(),
    )
    val backupInterval: MutableStateFlow<Int> by IntSetting(defaultValue = 1, min = 0)
    val backupTTL: MutableStateFlow<Int> by IntSetting(defaultValue = 14.days.inWholeDays.toInt(), min = 0)

    // local source
    val localSourcePath: MutableStateFlow<String> by PathSetting(defaultValue = "", mustExist = true)

    // cloudflare bypass
    val flareSolverrEnabled: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)
    val flareSolverrUrl: MutableStateFlow<String> by StringSetting(defaultValue = "http://localhost:8191")
    val flareSolverrTimeout: MutableStateFlow<Int> by IntSetting(defaultValue = 60.seconds.inWholeSeconds.toInt(), min = 0)
    val flareSolverrSessionName: MutableStateFlow<String> by StringSetting(defaultValue = "suwayomi")
    val flareSolverrSessionTtl: MutableStateFlow<Int> by IntSetting(defaultValue = 15.minutes.inWholeMinutes.toInt(), min = 0)
    val flareSolverrAsResponseFallback: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)

    // opds settings
    val opdsUseBinaryFileSizes: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)
    val opdsItemsPerPage: MutableStateFlow<Int> by IntSetting(defaultValue = 100, min = 10, max = 5000)
    val opdsEnablePageReadProgress: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = true)
    val opdsMarkAsReadOnDownload: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)
    val opdsShowOnlyUnreadChapters: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)
    val opdsShowOnlyDownloadedChapters: MutableStateFlow<Boolean> by BooleanSetting(defaultValue = false)
    val opdsChapterSortOrder: MutableStateFlow<SortOrder> by EnumSetting(defaultValue = SortOrder.DESC)

    // koreader sync
    val koreaderSyncServerUrl: MutableStateFlow<String> by StringSetting(defaultValue = "http://localhost:17200")
    val koreaderSyncUsername: MutableStateFlow<String> by StringSetting(defaultValue = "")
    val koreaderSyncUserkey: MutableStateFlow<String> by StringSetting(defaultValue = "")
    val koreaderSyncDeviceId: MutableStateFlow<String> by StringSetting(defaultValue = "")
    val koreaderSyncChecksumMethod: MutableStateFlow<KoreaderSyncChecksumMethod> by EnumSetting(
        defaultValue = KoreaderSyncChecksumMethod.BINARY,
    )
    val koreaderSyncStrategy: MutableStateFlow<KoreaderSyncStrategy> by EnumSetting(defaultValue = KoreaderSyncStrategy.DISABLED)
    val koreaderSyncPercentageTolerance: MutableStateFlow<Double> by DoubleSetting(defaultValue = 1e-15, min = 1e-15, max = 1.0)

    // Deprecated settings (using MigratedConfigValue for proper mapping)
    val basicAuthEnabled: MutableStateFlow<Boolean> by MigratedConfigValue(
        readMigrated = { authMode.value == AuthMode.BASIC_AUTH },
        setMigrated = { authMode.value = if (it) AuthMode.BASIC_AUTH else AuthMode.NONE },
    )

    val basicAuthUsername: MutableStateFlow<String> by MigratedConfigValue(
        readMigrated = { authUsername.value },
        setMigrated = { authUsername.value = it },
    )

    val basicAuthPassword: MutableStateFlow<String> by MigratedConfigValue(
        readMigrated = { authPassword.value },
        setMigrated = { authPassword.value = it },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> subscribeTo(
        flow: Flow<T>,
        onChange: suspend (value: T) -> Unit,
        ignoreInitialValue: Boolean = true,
    ) {
        val actualFlow =
            if (ignoreInitialValue) {
                flow.drop(1)
            } else {
                flow
            }

        val sharedFlow = MutableSharedFlow<T>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        actualFlow.distinctUntilChanged().mapLatest { sharedFlow.emit(it) }.launchIn(mutableConfigValueScope)
        sharedFlow.onEach { onChange(it) }.launchIn(mutableConfigValueScope)
    }

    fun <T> subscribeTo(
        flow: Flow<T>,
        onChange: suspend () -> Unit,
        ignoreInitialValue: Boolean = true,
    ) {
        subscribeTo(flow, { _ -> onChange() }, ignoreInitialValue)
    }

    fun <T> subscribeTo(
        mutableStateFlow: MutableStateFlow<T>,
        onChange: suspend (value: T) -> Unit,
        ignoreInitialValue: Boolean = true,
    ) {
        subscribeTo(mutableStateFlow.asStateFlow(), onChange, ignoreInitialValue)
    }

    fun <T> subscribeTo(
        mutableStateFlow: MutableStateFlow<T>,
        onChange: suspend () -> Unit,
        ignoreInitialValue: Boolean = true,
    ) {
        subscribeTo(mutableStateFlow.asStateFlow(), { _ -> onChange() }, ignoreInitialValue)
    }

    companion object {
        fun register(getConfig: () -> Config) =
            ServerConfig {
                getConfig().getConfig(
                    SERVER_CONFIG_MODULE_NAME,
                )
            }
    }
}
