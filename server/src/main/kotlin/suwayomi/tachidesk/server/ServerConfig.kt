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

class ServerConfig(
    getConfig: () -> Config,
) : SystemPropertyOverridableConfigModule(
        getConfig,
        SERVER_CONFIG_MODULE_NAME,
    ) {
    /********************************/
    /**                           **/
    /**          Network          **/
    /**                           **/
    /*******************************/
    val ip: MutableStateFlow<String> by StringSetting(
        defaultValue = "0.0.0.0",
        pattern = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$".toRegex(),
        group = SettingGroup.NETWORK,
    )

    val port: MutableStateFlow<Int> by IntSetting(
        defaultValue = 4567,
        min = 1,
        max = 65535,
        group = SettingGroup.NETWORK,
    )

    /*******************************/
    /**                           **/
    /**           Proxy           **/
    /**                           **/

    /*******************************/
    val socksProxyEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.PROXY,
    )

    val socksProxyVersion: MutableStateFlow<Int> by IntSetting(
        defaultValue = 5,
        min = 4,
        max = 5,
        group = SettingGroup.PROXY,
    )

    val socksProxyHost: MutableStateFlow<String> by StringSetting(
        defaultValue = "",
        group = SettingGroup.PROXY,
    )

    val socksProxyPort: MutableStateFlow<String> by StringSetting(
        defaultValue = "",
        group = SettingGroup.PROXY,
    )

    val socksProxyUsername: MutableStateFlow<String> by StringSetting(
        defaultValue = "",
        group = SettingGroup.PROXY,
    )

    val socksProxyPassword: MutableStateFlow<String> by StringSetting(
        defaultValue = "",
        group = SettingGroup.PROXY,
    )

    /*******************************/
    /**                           **/
    /**           WebUI           **/
    /**                           **/

    /*******************************/
    val webUIEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = true,
        group = SettingGroup.WEB_UI,
        requiresRestart = false,
    )

    val webUIFlavor: MutableStateFlow<WebUIFlavor> by EnumSetting(
        defaultValue = WebUIFlavor.WEBUI,
        enumClass = WebUIFlavor::class,
        group = SettingGroup.WEB_UI,
    )

    val initialOpenInBrowserEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = true,
        group = SettingGroup.WEB_UI,
        description = "Open client on startup",
    )

    val webUIInterface: MutableStateFlow<WebUIInterface> by EnumSetting(
        defaultValue = WebUIInterface.BROWSER,
        enumClass = WebUIInterface::class,
        group = SettingGroup.WEB_UI,
    )

    val electronPath: MutableStateFlow<String> by PathSetting(
        defaultValue = "",
        mustExist = true,
        group = SettingGroup.WEB_UI,
    )

    val webUIChannel: MutableStateFlow<WebUIChannel> by EnumSetting(
        defaultValue = WebUIChannel.STABLE,
        enumClass = WebUIChannel::class,
        group = SettingGroup.WEB_UI,
    )

    val webUIUpdateCheckInterval: MutableStateFlow<Double> by DisableableDoubleSetting(
        defaultValue = 23.hours.inWholeHours.toDouble(),
        min = 0.0,
        max = 23.0,
        group = SettingGroup.WEB_UI,
        description = "Time in hours",
    )

    /*******************************/
    /**                           **/
    /**         Downloader        **/
    /**                           **/

    /*******************************/
    val downloadAsCbz: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.DOWNLOADER,
    )

    val downloadsPath: MutableStateFlow<String> by PathSetting(
        defaultValue = "",
        mustExist = true,
        group = SettingGroup.DOWNLOADER,
    )

    val autoDownloadNewChapters: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.DOWNLOADER,
    )

    val excludeEntryWithUnreadChapters: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = true,
        group = SettingGroup.DOWNLOADER,
        description = "Exclude entries with unread chapters from auto-download",
    )

    val autoDownloadNewChaptersLimit: MutableStateFlow<Int> by DisableableIntSetting(
        defaultValue = 0,
        min = 0,
        group = SettingGroup.DOWNLOADER,
        description = "Maximum number of new chapters to auto-download",
    )

    val autoDownloadIgnoreReUploads: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.DOWNLOADER,
        description = "Ignore re-uploaded chapters from auto-download",
    )

    val downloadConversions: MutableStateFlow<Map<String, DownloadConversion>> by MapSetting<String, DownloadConversion>(
        defaultValue = emptyMap(),
        group = SettingGroup.DOWNLOADER,
        typeInfo =
            SettingsRegistry.PartialTypeInfo(
                specificType = "List<SettingsDownloadConversionType>",
                interfaceType = "List<SettingsDownloadConversion>",
                imports =
                    listOf(
                        "suwayomi.tachidesk.graphql.types.SettingsDownloadConversion",
                        "suwayomi.tachidesk.graphql.types.SettingsDownloadConversionType",
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

    /*******************************/
    /**                           **/
    /**      Extension/Source     **/
    /**                           **/

    /*******************************/
    val extensionRepos: MutableStateFlow<List<String>> by ListSetting<String>(
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
        group = SettingGroup.EXTENSION,
        typeInfo =
            SettingsRegistry.PartialTypeInfo(
                specificType = "List<String>",
            ),
        description = "example: [\"https://github.com/MY_ACCOUNT/MY_REPO/tree/repo\"]",
    )

    val maxSourcesInParallel: MutableStateFlow<Int> by IntSetting(
        defaultValue = 6,
        min = 1,
        max = 20,
        group = SettingGroup.EXTENSION,
        description =
            "How many different sources can do requests (library update, downloads) in parallel. " +
                "Library update/downloads are grouped by source and all manga of a source are updated/downloaded synchronously",
    )

    /*******************************/
    /**                           **/
    /**      Library updates      **/
    /**                           **/

    /*******************************/
    val excludeUnreadChapters: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = true,
        group = SettingGroup.LIBRARY_UPDATES,
    )

    val excludeNotStarted: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = true,
        group = SettingGroup.LIBRARY_UPDATES,
    )

    val excludeCompleted: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = true,
        group = SettingGroup.LIBRARY_UPDATES,
    )

    val globalUpdateInterval: MutableStateFlow<Double> by DisableableDoubleSetting(
        defaultValue = 12.hours.inWholeHours.toDouble(),
        min = 6.0,
        group = SettingGroup.LIBRARY_UPDATES,
        description = "Time in hours",
    )

    val updateMangas: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.LIBRARY_UPDATES,
        description = "Update manga metadata and thumbnail along with the chapter list update during the library update.",
    )

    /*******************************/
    /**                           **/
    /**       Authentication      **/
    /**                           **/

    /*******************************/
    val authMode: MutableStateFlow<AuthMode> by EnumSetting(
        defaultValue = AuthMode.NONE,
        enumClass = AuthMode::class,
        group = SettingGroup.AUTH,
    )

    val authUsername: MutableStateFlow<String> by StringSetting(
        defaultValue = "",
        group = SettingGroup.AUTH,
    )

    val authPassword: MutableStateFlow<String> by StringSetting(
        defaultValue = "",
        group = SettingGroup.AUTH,
    )

    val jwtAudience: MutableStateFlow<String> by StringSetting(
        defaultValue = "suwayomi-server-api",
        group = SettingGroup.AUTH,
    )

    val jwtTokenExpiry: MutableStateFlow<Duration> by DurationSetting(
        defaultValue = 5.minutes,
        min = 0.seconds,
        group = SettingGroup.AUTH,
    )

    val jwtRefreshExpiry: MutableStateFlow<Duration> by DurationSetting(
        defaultValue = 60.days,
        min = 0.seconds,
        group = SettingGroup.AUTH,
    )

    /*******************************/
    /**                           **/
    /**            Misc           **/
    /**                           **/

    /*******************************/
    val debugLogsEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.MISC,
    )

    val systemTrayEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = true,
        group = SettingGroup.MISC,
    )

    val maxLogFiles: MutableStateFlow<Int> by IntSetting(
        defaultValue = 31,
        min = 0,
        group = SettingGroup.MISC,
        description = "The max number of days to keep files before they get deleted",
    )

    private val logbackSizePattern = "^[0-9]+(|kb|KB|mb|MB|gb|GB)$".toRegex()
    val maxLogFileSize: MutableStateFlow<String> by StringSetting(
        defaultValue = "10mb",
        pattern = logbackSizePattern,
        group = SettingGroup.MISC,
        description = "Maximum log file size - values: 1 (bytes), 1KB (kilobytes), 1MB (megabytes), 1GB (gigabytes)",
    )

    val maxLogFolderSize: MutableStateFlow<String> by StringSetting(
        defaultValue = "100mb",
        pattern = logbackSizePattern,
        group = SettingGroup.MISC,
        description = "Maximum log folder size - values: 1 (bytes), 1KB (kilobytes), 1MB (megabytes), 1GB (gigabytes)",
    )

    /*******************************/
    /**                           **/
    /**           Backup          **/
    /**                           **/

    /*******************************/
    val backupPath: MutableStateFlow<String> by PathSetting(
        defaultValue = "",
        mustExist = true,
        group = SettingGroup.BACKUP,
    )

    val backupTime: MutableStateFlow<String> by StringSetting(
        defaultValue = "00:00",
        pattern = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$".toRegex(),
        group = SettingGroup.BACKUP,
        description = "Daily backup time (HH:MM) ; range: [00:00, 23:59]",
    )

    val backupInterval: MutableStateFlow<Int> by DisableableIntSetting(
        defaultValue = 1,
        min = 0,
        group = SettingGroup.BACKUP,
        description = "Time in days",
    )

    val backupTTL: MutableStateFlow<Int> by DisableableIntSetting(
        defaultValue = 14.days.inWholeDays.toInt(),
        min = 0,
        group = SettingGroup.BACKUP,
        description = "Backup retention in days",
    )

    /*******************************/
    /**                           **/
    /**        Local source       **/
    /**                           **/

    /*******************************/
    val localSourcePath: MutableStateFlow<String> by PathSetting(
        defaultValue = "",
        mustExist = true,
        group = SettingGroup.LOCAL_SOURCE,
    )

    /*******************************/
    /**                           **/
    /**         Cloudflare        **/
    /**                           **/

    /*******************************/
    val flareSolverrEnabled: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.CLOUDFLARE,
    )

    val flareSolverrUrl: MutableStateFlow<String> by StringSetting(
        defaultValue = "http://localhost:8191",
        group = SettingGroup.CLOUDFLARE,
    )

    val flareSolverrTimeout: MutableStateFlow<Int> by IntSetting(
        defaultValue = 60.seconds.inWholeSeconds.toInt(),
        min = 0,
        group = SettingGroup.CLOUDFLARE,
        description = "Time in seconds",
    )

    val flareSolverrSessionName: MutableStateFlow<String> by StringSetting(
        defaultValue = "suwayomi",
        group = SettingGroup.CLOUDFLARE,
    )

    val flareSolverrSessionTtl: MutableStateFlow<Int> by IntSetting(
        defaultValue = 15.minutes.inWholeMinutes.toInt(),
        min = 0,
        group = SettingGroup.CLOUDFLARE,
        description = "Time in minutes",
    )

    val flareSolverrAsResponseFallback: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.CLOUDFLARE,
    )

    /*******************************/
    /**                           **/
    /**            OPDS           **/
    /**                           **/

    /*******************************/
    val opdsUseBinaryFileSizes: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.OPDS,
        description = "Display file size in binary (KiB, MiB, GiB) instead of decimal (KB, MB, GB)",
    )

    val opdsItemsPerPage: MutableStateFlow<Int> by IntSetting(
        defaultValue = 100,
        min = 10,
        max = 5000,
        group = SettingGroup.OPDS,
    )

    val opdsEnablePageReadProgress: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = true,
        group = SettingGroup.OPDS,
    )

    val opdsMarkAsReadOnDownload: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.OPDS,
    )

    val opdsShowOnlyUnreadChapters: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.OPDS,
    )

    val opdsShowOnlyDownloadedChapters: MutableStateFlow<Boolean> by BooleanSetting(
        defaultValue = false,
        group = SettingGroup.OPDS,
    )

    val opdsChapterSortOrder: MutableStateFlow<SortOrder> by EnumSetting(
        defaultValue = SortOrder.DESC,
        enumClass = SortOrder::class,
        group = SettingGroup.OPDS,
    )

    /*******************************/
    /**                           **/
    /**       KOReader sync       **/
    /**                           **/

    /*******************************/
    val koreaderSyncServerUrl: MutableStateFlow<String> by StringSetting(
        defaultValue = "http://localhost:17200",
        group = SettingGroup.KOREADER_SYNC,
    )

    val koreaderSyncUsername: MutableStateFlow<String> by StringSetting(
        defaultValue = "",
        group = SettingGroup.KOREADER_SYNC,
    )

    val koreaderSyncUserkey: MutableStateFlow<String> by StringSetting(
        defaultValue = "",
        group = SettingGroup.KOREADER_SYNC,
    )

    val koreaderSyncDeviceId: MutableStateFlow<String> by StringSetting(
        defaultValue = "",
        group = SettingGroup.KOREADER_SYNC,
    )

    val koreaderSyncChecksumMethod: MutableStateFlow<KoreaderSyncChecksumMethod> by EnumSetting(
        defaultValue = KoreaderSyncChecksumMethod.BINARY,
        enumClass = KoreaderSyncChecksumMethod::class,
        group = SettingGroup.KOREADER_SYNC,
    )

    val koreaderSyncStrategy: MutableStateFlow<KoreaderSyncStrategy> by EnumSetting(
        defaultValue = KoreaderSyncStrategy.DISABLED,
        enumClass = KoreaderSyncStrategy::class,
        group = SettingGroup.KOREADER_SYNC,
    )

    val koreaderSyncPercentageTolerance: MutableStateFlow<Double> by DoubleSetting(
        defaultValue = 0.000000000000001,
        min = 0.000000000000001,
        max = 1.0,
        group = SettingGroup.KOREADER_SYNC,
        description = "Absolute tolerance for progress comparison",
    )

    /***********************************************************************/
    /**                                                                   **/
    /**                        Deprecated settings                        **/
    /**           (using MigratedConfigValue for proper mapping)          **/
    /**                                                                   **/

    /***********************************************************************/
    val basicAuthEnabled: MutableStateFlow<Boolean> by MigratedConfigValue(
        defaultValue = false,
        group = SettingGroup.AUTH,
        deprecated =
            SettingsRegistry.SettingDeprecated(
                replaceWith = "authMode",
                message = "Removed - prefer authUsername",
            ),
        readMigrated = { authMode.value == AuthMode.BASIC_AUTH },
        setMigrated = { authMode.value = if (it) AuthMode.BASIC_AUTH else AuthMode.NONE },
    )

    val basicAuthUsername: MutableStateFlow<String> by MigratedConfigValue(
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

    val autoDownloadAheadLimit: MutableStateFlow<Int> by MigratedConfigValue(
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

    val gqlDebugLogsEnabled: MutableStateFlow<Boolean> by MigratedConfigValue(
        defaultValue = false,
        group = SettingGroup.MISC,
        deprecated =
            SettingsRegistry.SettingDeprecated(
                message = "Removed - does not do anything",
            ),
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
