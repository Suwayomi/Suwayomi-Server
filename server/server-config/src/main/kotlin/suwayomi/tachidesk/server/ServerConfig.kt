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
import suwayomi.tachidesk.graphql.types.DownloadConversion
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncStrategy
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionType
import suwayomi.tachidesk.graphql.types.WebUIChannel
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.graphql.types.WebUIInterface
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionType
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
import suwayomi.tachidesk.server.settings.SettingGroup
import suwayomi.tachidesk.server.settings.SettingsRegistry
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

// Settings are ordered by "protoNumber".
class ServerConfig(
    getConfig: () -> Config,
) : SystemPropertyOverridableConfigModule(
        getConfig,
        SERVER_CONFIG_MODULE_NAME,
    ) {
    val ip: MutableStateFlow<String> by StringSetting(
        protoNumber = 1,
        group = SettingGroup.NETWORK,
        defaultValue = "0.0.0.0",
        pattern = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$".toRegex(),
    )

    val port: MutableStateFlow<Int> by IntSetting(
        protoNumber = 2,
        group = SettingGroup.NETWORK,
        defaultValue = 4567,
        min = 1,
        max = 65535,
    )

    val socksProxyEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 3,
        group = SettingGroup.PROXY,
        defaultValue = false,
    )

    val socksProxyVersion: MutableStateFlow<Int> by IntSetting(
        protoNumber = 4,
        group = SettingGroup.PROXY,
        defaultValue = 5,
        min = 4,
        max = 5,
    )

    val socksProxyHost: MutableStateFlow<String> by StringSetting(
        protoNumber = 5,
        group = SettingGroup.PROXY,
        defaultValue = "",
    )

    val socksProxyPort: MutableStateFlow<String> by StringSetting(
        protoNumber = 6,
        group = SettingGroup.PROXY,
        defaultValue = "",
    )

    val socksProxyUsername: MutableStateFlow<String> by StringSetting(
        protoNumber = 7,
        group = SettingGroup.PROXY,
        defaultValue = "",
    )

    val socksProxyPassword: MutableStateFlow<String> by StringSetting(
        protoNumber = 8,
        group = SettingGroup.PROXY,
        defaultValue = "",
    )

    val webUIFlavor: MutableStateFlow<WebUIFlavor> by EnumSetting(
        protoNumber = 9,
        group = SettingGroup.WEB_UI,
        defaultValue = WebUIFlavor.WEBUI,
        enumClass = WebUIFlavor::class,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.WebUIFlavor")),
    )

    val initialOpenInBrowserEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 10,
        group = SettingGroup.WEB_UI,
        defaultValue = true,
        description = "Open client on startup",
    )

    val webUIInterface: MutableStateFlow<WebUIInterface> by EnumSetting(
        protoNumber = 11,
        group = SettingGroup.WEB_UI,
        defaultValue = WebUIInterface.BROWSER,
        enumClass = WebUIInterface::class,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.WebUIInterface")),
    )

    val electronPath: MutableStateFlow<String> by PathSetting(
        protoNumber = 12,
        group = SettingGroup.WEB_UI,
        defaultValue = "",
        mustExist = true,
    )

    val webUIChannel: MutableStateFlow<WebUIChannel> by EnumSetting(
        protoNumber = 13,
        group = SettingGroup.WEB_UI,
        defaultValue = WebUIChannel.STABLE,
        enumClass = WebUIChannel::class,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.WebUIChannel")),
    )

    val webUIUpdateCheckInterval: MutableStateFlow<Double> by DisableableDoubleSetting(
        protoNumber = 14,
        group = SettingGroup.WEB_UI,
        defaultValue = 23.hours.inWholeHours.toDouble(),
        min = 0.0,
        max = 23.0,
        description = "Time in hours",
    )

    val downloadAsCbz: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 15,
        defaultValue = false,
        group = SettingGroup.DOWNLOADER,
    )

    val downloadsPath: MutableStateFlow<String> by PathSetting(
        protoNumber = 16,
        group = SettingGroup.DOWNLOADER,
        defaultValue = "",
        mustExist = true,
    )

    val autoDownloadNewChapters: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 17,
        defaultValue = false,
        group = SettingGroup.DOWNLOADER,
    )

    val excludeEntryWithUnreadChapters: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 18,
        group = SettingGroup.DOWNLOADER,
        defaultValue = true,
        description = "Exclude entries with unread chapters from auto-download",
    )

    val autoDownloadAheadLimit: MutableStateFlow<Int> by MigratedConfigValue(
        protoNumber = 19,
        defaultValue = 0,
        group = SettingGroup.DOWNLOADER,
        deprecated =
            SettingsRegistry.SettingDeprecated(
                replaceWith = "autoDownloadNewChaptersLimit",
                message = "Replaced with autoDownloadNewChaptersLimit",
            ),
        readMigrated = { autoDownloadNewChaptersLimit.value },
        setMigrated = { autoDownloadNewChaptersLimit.value = it },
    )

    val autoDownloadNewChaptersLimit: MutableStateFlow<Int> by DisableableIntSetting(
        protoNumber = 20,
        group = SettingGroup.DOWNLOADER,
        defaultValue = 0,
        min = 0,
        description = "Maximum number of new chapters to auto-download",
    )

    val autoDownloadIgnoreReUploads: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 21,
        group = SettingGroup.DOWNLOADER,
        defaultValue = false,
        description = "Ignore re-uploaded chapters from auto-download",
    )

    val extensionRepos: MutableStateFlow<List<String>> by ListSetting<String>(
        protoNumber = 22,
        group = SettingGroup.EXTENSION,
        defaultValue = emptyList(),
        itemValidator = { url ->
            if (url.matches(repoMatchRegex)) {
                null
            } else {
                "Invalid repository URL format"
            }
        },
        itemToValidValue = { url ->
            if (url.matches(repoMatchRegex)) {
                url
            } else {
                null
            }
        },
        typeInfo =
            SettingsRegistry.PartialTypeInfo(
                specificType = "List<String>",
            ),
        description = "example: [\"https://github.com/MY_ACCOUNT/MY_REPO/tree/repo\"]",
    )

    val maxSourcesInParallel: MutableStateFlow<Int> by IntSetting(
        protoNumber = 23,
        group = SettingGroup.EXTENSION,
        defaultValue = 6,
        min = 1,
        max = 20,
        description =
            "How many different sources can do requests (library update, downloads) in parallel. " +
                "Library update/downloads are grouped by source and all manga of a source are updated/downloaded synchronously",
    )

    val excludeUnreadChapters: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 24,
        defaultValue = true,
        group = SettingGroup.LIBRARY_UPDATES,
    )

    val excludeNotStarted: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 25,
        defaultValue = true,
        group = SettingGroup.LIBRARY_UPDATES,
    )

    val excludeCompleted: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 26,
        defaultValue = true,
        group = SettingGroup.LIBRARY_UPDATES,
    )

    val globalUpdateInterval: MutableStateFlow<Double> by DisableableDoubleSetting(
        protoNumber = 27,
        group = SettingGroup.LIBRARY_UPDATES,
        defaultValue = 12.hours.inWholeHours.toDouble(),
        min = 6.0,
        description = "Time in hours",
    )

    val updateMangas: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 28,
        group = SettingGroup.LIBRARY_UPDATES,
        defaultValue = false,
        description = "Update manga metadata and thumbnail along with the chapter list update during the library update.",
    )

    val basicAuthEnabled: MutableStateFlow<Boolean> by MigratedConfigValue(
        protoNumber = 29,
        defaultValue = false,
        group = SettingGroup.AUTH,
        deprecated =
            SettingsRegistry.SettingDeprecated(
                replaceWith = "authMode",
                message = "Removed - prefer authMode",
            ),
        readMigrated = { authMode.value == AuthMode.BASIC_AUTH },
        setMigrated = { authMode.value = if (it) AuthMode.BASIC_AUTH else AuthMode.NONE },
        typeInfo =
            SettingsRegistry.PartialTypeInfo(
                restoreLegacy = { value ->
                    value.takeIf { authMode.value == AuthMode.NONE }
                },
            ),
    )

    val authUsername: MutableStateFlow<String> by StringSetting(
        protoNumber = 30,
        group = SettingGroup.AUTH,
        defaultValue = "",
    )

    val authPassword: MutableStateFlow<String> by StringSetting(
        protoNumber = 31,
        group = SettingGroup.AUTH,
        defaultValue = "",
    )

    val debugLogsEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 32,
        defaultValue = false,
        group = SettingGroup.MISC,
    )

    val gqlDebugLogsEnabled: MutableStateFlow<Boolean> by MigratedConfigValue(
        protoNumber = 33,
        defaultValue = false,
        group = SettingGroup.MISC,
        deprecated =
            SettingsRegistry.SettingDeprecated(
                message = "Removed - does not do anything",
            ),
    )

    val systemTrayEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 34,
        defaultValue = true,
        group = SettingGroup.MISC,
    )

    val maxLogFiles: MutableStateFlow<Int> by IntSetting(
        protoNumber = 35,
        group = SettingGroup.MISC,
        defaultValue = 31,
        min = 0,
        description = "The max number of days to keep files before they get deleted",
    )

    private val logbackSizePattern = "^[0-9]+(|kb|KB|mb|MB|gb|GB)$".toRegex()
    val maxLogFileSize: MutableStateFlow<String> by StringSetting(
        protoNumber = 36,
        group = SettingGroup.MISC,
        defaultValue = "10mb",
        pattern = logbackSizePattern,
        description = "Maximum log file size - values: 1 (bytes), 1KB (kilobytes), 1MB (megabytes), 1GB (gigabytes)",
    )

    val maxLogFolderSize: MutableStateFlow<String> by StringSetting(
        protoNumber = 37,
        group = SettingGroup.MISC,
        defaultValue = "100mb",
        pattern = logbackSizePattern,
        description = "Maximum log folder size - values: 1 (bytes), 1KB (kilobytes), 1MB (megabytes), 1GB (gigabytes)",
    )

    val backupPath: MutableStateFlow<String> by PathSetting(
        protoNumber = 38,
        group = SettingGroup.BACKUP,
        defaultValue = "",
        mustExist = true,
    )

    val backupTime: MutableStateFlow<String> by StringSetting(
        protoNumber = 39,
        group = SettingGroup.BACKUP,
        defaultValue = "00:00",
        pattern = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$".toRegex(),
        description = "Daily backup time (HH:MM) ; range: [00:00, 23:59]",
    )

    val backupInterval: MutableStateFlow<Int> by DisableableIntSetting(
        protoNumber = 40,
        group = SettingGroup.BACKUP,
        defaultValue = 1,
        min = 0,
        description = "Time in days",
    )

    val backupTTL: MutableStateFlow<Int> by DisableableIntSetting(
        protoNumber = 41,
        group = SettingGroup.BACKUP,
        defaultValue = 14.days.inWholeDays.toInt(),
        min = 0,
        description = "Backup retention in days",
    )

    val localSourcePath: MutableStateFlow<String> by PathSetting(
        protoNumber = 42,
        group = SettingGroup.LOCAL_SOURCE,
        defaultValue = "",
        mustExist = true,
    )

    val flareSolverrEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 43,
        defaultValue = false,
        group = SettingGroup.CLOUDFLARE,
    )

    val flareSolverrUrl: MutableStateFlow<String> by StringSetting(
        protoNumber = 44,
        group = SettingGroup.CLOUDFLARE,
        defaultValue = "http://localhost:8191",
    )

    val flareSolverrTimeout: MutableStateFlow<Int> by IntSetting(
        protoNumber = 45,
        group = SettingGroup.CLOUDFLARE,
        defaultValue = 60.seconds.inWholeSeconds.toInt(),
        min = 0,
        description = "Time in seconds",
    )

    val flareSolverrSessionName: MutableStateFlow<String> by StringSetting(
        protoNumber = 46,
        group = SettingGroup.CLOUDFLARE,
        defaultValue = "suwayomi",
    )

    val flareSolverrSessionTtl: MutableStateFlow<Int> by IntSetting(
        protoNumber = 47,
        group = SettingGroup.CLOUDFLARE,
        defaultValue = 15.minutes.inWholeMinutes.toInt(),
        min = 0,
        description = "Time in minutes",
    )

    val flareSolverrAsResponseFallback: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 48,
        defaultValue = false,
        group = SettingGroup.CLOUDFLARE,
    )

    val opdsUseBinaryFileSizes: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 49,
        group = SettingGroup.OPDS,
        defaultValue = false,
        description = "Display file size in binary (KiB, MiB, GiB) instead of decimal (KB, MB, GB)",
    )

    val opdsItemsPerPage: MutableStateFlow<Int> by IntSetting(
        protoNumber = 50,
        group = SettingGroup.OPDS,
        defaultValue = 100,
        min = 10,
        max = 5000,
    )

    val opdsEnablePageReadProgress: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 51,
        defaultValue = true,
        group = SettingGroup.OPDS,
    )

    val opdsMarkAsReadOnDownload: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 52,
        defaultValue = false,
        group = SettingGroup.OPDS,
    )

    val opdsShowOnlyUnreadChapters: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 53,
        defaultValue = false,
        group = SettingGroup.OPDS,
    )

    val opdsShowOnlyDownloadedChapters: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 54,
        defaultValue = false,
        group = SettingGroup.OPDS,
    )

    val opdsChapterSortOrder: MutableStateFlow<SortOrder> by EnumSetting(
        protoNumber = 55,
        group = SettingGroup.OPDS,
        defaultValue = SortOrder.DESC,
        enumClass = SortOrder::class,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("org.jetbrains.exposed.sql.SortOrder")),
    )

    val authMode: MutableStateFlow<AuthMode> by EnumSetting(
        protoNumber = 56,
        group = SettingGroup.AUTH,
        defaultValue = AuthMode.NONE,
        enumClass = AuthMode::class,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.AuthMode")),
    )

    val downloadConversions: MutableStateFlow<Map<String, DownloadConversion>> by MapSetting<String, DownloadConversion>(
        protoNumber = 57,
        defaultValue = emptyMap(),
        group = SettingGroup.DOWNLOADER,
        typeInfo =
            SettingsRegistry.PartialTypeInfo(
                specificType = "List<SettingsDownloadConversionType>",
                interfaceType = "List<SettingsDownloadConversion>",
                backupType = "List<BackupSettingsDownloadConversionType>",
                imports =
                    listOf(
                        "suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionType",
                    ),
                convertToGqlType = { value ->
                    @Suppress("UNCHECKED_CAST")
                    val castedValue = value as Map<String, DownloadConversion>

                    castedValue.map {
                        SettingsDownloadConversionType(
                            it.key,
                            it.value.target,
                            it.value.compressionLevel,
                        )
                    }
                },
                convertToInternalType = { list ->
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
                convertToBackupType = { value ->
                    @Suppress("UNCHECKED_CAST")
                    val castedValue = value as Map<String, DownloadConversion>

                    castedValue.map {
                        BackupSettingsDownloadConversionType(
                            it.key,
                            it.value.target,
                            it.value.compressionLevel,
                        )
                    }
                },
            ),
        description =
            """
            map input mime type to conversion information, or "default" for others
            server.downloadConversions."image/webp" = {
              target = "image/jpeg"   # image type to convert to
              compressionLevel = 0.8  # quality in range [0,1], leave away to use default compression
            }
            """.trimIndent(),
    )

    val jwtAudience: MutableStateFlow<String> by StringSetting(
        protoNumber = 58,
        group = SettingGroup.AUTH,
        defaultValue = "suwayomi-server-api",
    )

    val koreaderSyncServerUrl: MutableStateFlow<String> by StringSetting(
        protoNumber = 59,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = "http://localhost:17200",
    )

    val koreaderSyncUsername: MutableStateFlow<String> by StringSetting(
        protoNumber = 60,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = "",
    )

    val koreaderSyncUserkey: MutableStateFlow<String> by StringSetting(
        protoNumber = 61,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = "",
    )

    val koreaderSyncDeviceId: MutableStateFlow<String> by StringSetting(
        protoNumber = 62,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = "",
    )

    val koreaderSyncChecksumMethod: MutableStateFlow<KoreaderSyncChecksumMethod> by EnumSetting(
        protoNumber = 63,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = KoreaderSyncChecksumMethod.BINARY,
        enumClass = KoreaderSyncChecksumMethod::class,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod")),
    )

    val koreaderSyncStrategy: MutableStateFlow<KoreaderSyncStrategy> by EnumSetting(
        protoNumber = 64,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = KoreaderSyncStrategy.DISABLED,
        enumClass = KoreaderSyncStrategy::class,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.KoreaderSyncStrategy")),
    )

    val koreaderSyncPercentageTolerance: MutableStateFlow<Double> by DoubleSetting(
        protoNumber = 65,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = 0.000000000000001,
        min = 0.000000000000001,
        max = 1.0,
        description = "Absolute tolerance for progress comparison",
    )

    val jwtTokenExpiry: MutableStateFlow<Duration> by DurationSetting(
        protoNumber = 66,
        group = SettingGroup.AUTH,
        defaultValue = 5.minutes,
        min = 0.seconds,
    )

    val jwtRefreshExpiry: MutableStateFlow<Duration> by DurationSetting(
        protoNumber = 67,
        group = SettingGroup.AUTH,
        defaultValue = 60.days,
        min = 0.seconds,
    )

    val webUIEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        protoNumber = 68,
        group = SettingGroup.WEB_UI,
        defaultValue = true,
        requiresRestart = true,
    )

    /** ****************************************************************** **/
    /**                                                                    **/
    /**                          Renamed settings                          **/
    /**                                                                    **/

    /** ****************************************************************** **/
    val basicAuthUsername: MutableStateFlow<String> by MigratedConfigValue(
        protoNumber = 99991,
        defaultValue = "",
        group = SettingGroup.AUTH,
        deprecated =
            SettingsRegistry.SettingDeprecated(
                replaceWith = "authUsername",
                message = "Removed - prefer authUsername",
            ),
        readMigrated = { authUsername.value },
        setMigrated = { authUsername.value = it },
    )

    val basicAuthPassword: MutableStateFlow<String> by MigratedConfigValue(
        protoNumber = 99992,
        defaultValue = "",
        group = SettingGroup.AUTH,
        deprecated =
            SettingsRegistry.SettingDeprecated(
                replaceWith = "authPassword",
                message = "Removed - prefer authPassword",
            ),
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
