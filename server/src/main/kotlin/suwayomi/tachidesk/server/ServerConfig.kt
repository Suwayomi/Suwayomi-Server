package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.typesafe.config.Config
import io.github.config4k.getValue
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import org.jetbrains.exposed.sql.SortOrder
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.graphql.types.WebUIChannel
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.graphql.types.WebUIInterface
import xyz.nulldev.ts.config.GlobalConfigManager
import xyz.nulldev.ts.config.SystemPropertyOverridableConfigModule
import kotlin.reflect.KProperty
import kotlin.time.Duration

val mutableConfigValueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

const val SERVER_CONFIG_MODULE_NAME = "server"

class ServerConfig(
    getConfig: () -> Config,
) : SystemPropertyOverridableConfigModule(
        getConfig,
        SERVER_CONFIG_MODULE_NAME,
    ) {
    open inner class OverrideConfigValue {
        var flow: MutableStateFlow<Any>? = null

        inline operator fun <reified T : MutableStateFlow<R>, reified R> getValue(
            thisRef: ServerConfig,
            property: KProperty<*>,
        ): T {
            if (flow != null) {
                return flow as T
            }

            val stateFlow = overridableConfig.getValue<ServerConfig, T>(thisRef, property)
            @Suppress("UNCHECKED_CAST")
            flow = stateFlow as MutableStateFlow<Any>

            stateFlow
                .drop(1)
                .distinctUntilChanged()
                .filter { it != thisRef.overridableConfig.getConfig().getValue<ServerConfig, R>(thisRef, property) }
                .onEach { GlobalConfigManager.updateValue("$moduleName.${property.name}", it as Any) }
                .launchIn(mutableConfigValueScope)

            return stateFlow
        }
    }

    open inner class MigratedConfigValue<T>(
        private val readMigrated: () -> T,
        private val setMigrated: (T) -> Unit,
    ) {
        private var flow: MutableStateFlow<T>? = null

        operator fun getValue(
            thisRef: ServerConfig,
            property: KProperty<*>,
        ): MutableStateFlow<T> {
            if (flow != null) {
                return flow!!
            }

            val value = readMigrated()

            val stateFlow = MutableStateFlow(value)
            flow = stateFlow

            stateFlow
                .drop(1)
                .distinctUntilChanged()
                .filter { it != readMigrated() }
                .onEach(setMigrated)
                .launchIn(mutableConfigValueScope)

            return stateFlow
        }
    }

    val ip: MutableStateFlow<String> by OverrideConfigValue()
    val port: MutableStateFlow<Int> by OverrideConfigValue()

    // proxy
    val socksProxyEnabled: MutableStateFlow<Boolean> by OverrideConfigValue()
    val socksProxyVersion: MutableStateFlow<Int> by OverrideConfigValue()
    val socksProxyHost: MutableStateFlow<String> by OverrideConfigValue()
    val socksProxyPort: MutableStateFlow<String> by OverrideConfigValue()
    val socksProxyUsername: MutableStateFlow<String> by OverrideConfigValue()
    val socksProxyPassword: MutableStateFlow<String> by OverrideConfigValue()

    // webUI
    val webUIEnabled: MutableStateFlow<Boolean> by OverrideConfigValue()
    val webUIFlavor: MutableStateFlow<WebUIFlavor> by OverrideConfigValue()
    val initialOpenInBrowserEnabled: MutableStateFlow<Boolean> by OverrideConfigValue()
    val webUIInterface: MutableStateFlow<WebUIInterface> by OverrideConfigValue()
    val electronPath: MutableStateFlow<String> by OverrideConfigValue()
    val webUIChannel: MutableStateFlow<WebUIChannel> by OverrideConfigValue()
    val webUIUpdateCheckInterval: MutableStateFlow<Double> by OverrideConfigValue()

    // downloader
    val downloadAsCbz: MutableStateFlow<Boolean> by OverrideConfigValue()
    val downloadsPath: MutableStateFlow<String> by OverrideConfigValue()
    val autoDownloadNewChapters: MutableStateFlow<Boolean> by OverrideConfigValue()
    val excludeEntryWithUnreadChapters: MutableStateFlow<Boolean> by OverrideConfigValue()
    val autoDownloadNewChaptersLimit: MutableStateFlow<Int> by OverrideConfigValue()
    val autoDownloadIgnoreReUploads: MutableStateFlow<Boolean> by OverrideConfigValue()
    val downloadConversions: MutableStateFlow<Map<String, DownloadConversion>> by OverrideConfigValue()

    data class DownloadConversion(
        val target: String,
        val compressionLevel: Double? = null,
    )

    // extensions
    val extensionRepos: MutableStateFlow<List<String>> by OverrideConfigValue()

    // requests
    val maxSourcesInParallel: MutableStateFlow<Int> by OverrideConfigValue()

    // updater
    val excludeUnreadChapters: MutableStateFlow<Boolean> by OverrideConfigValue()
    val excludeNotStarted: MutableStateFlow<Boolean> by OverrideConfigValue()
    val excludeCompleted: MutableStateFlow<Boolean> by OverrideConfigValue()
    val globalUpdateInterval: MutableStateFlow<Double> by OverrideConfigValue()
    val updateMangas: MutableStateFlow<Boolean> by OverrideConfigValue()

    // Authentication
    val authMode: MutableStateFlow<AuthMode> by OverrideConfigValue()
    val authUsername: MutableStateFlow<String> by OverrideConfigValue()
    val authPassword: MutableStateFlow<String> by OverrideConfigValue()
    val jwtAudience: MutableStateFlow<String> by OverrideConfigValue()
    val jwtTokenExpiry: MutableStateFlow<Duration> by OverrideConfigValue()
    val jwtRefreshExpiry: MutableStateFlow<Duration> by OverrideConfigValue()
    val basicAuthEnabled: MutableStateFlow<Boolean> by MigratedConfigValue({
        authMode.value == AuthMode.BASIC_AUTH
    }) {
        authMode.value = if (it) AuthMode.BASIC_AUTH else AuthMode.NONE
    }
    val basicAuthUsername: MutableStateFlow<String> by MigratedConfigValue({ authUsername.value }) {
        authUsername.value = it
    }
    val basicAuthPassword: MutableStateFlow<String> by MigratedConfigValue({ authPassword.value }) {
        authPassword.value = it
    }

    // misc
    val debugLogsEnabled: MutableStateFlow<Boolean> by OverrideConfigValue()
    val systemTrayEnabled: MutableStateFlow<Boolean> by OverrideConfigValue()
    val maxLogFiles: MutableStateFlow<Int> by OverrideConfigValue()
    val maxLogFileSize: MutableStateFlow<String> by OverrideConfigValue()
    val maxLogFolderSize: MutableStateFlow<String> by OverrideConfigValue()

    // backup
    val backupPath: MutableStateFlow<String> by OverrideConfigValue()
    val backupTime: MutableStateFlow<String> by OverrideConfigValue()
    val backupInterval: MutableStateFlow<Int> by OverrideConfigValue()
    val backupTTL: MutableStateFlow<Int> by OverrideConfigValue()

    // local source
    val localSourcePath: MutableStateFlow<String> by OverrideConfigValue()

    // cloudflare bypass
    val flareSolverrEnabled: MutableStateFlow<Boolean> by OverrideConfigValue()
    val flareSolverrUrl: MutableStateFlow<String> by OverrideConfigValue()
    val flareSolverrTimeout: MutableStateFlow<Int> by OverrideConfigValue()
    val flareSolverrSessionName: MutableStateFlow<String> by OverrideConfigValue()
    val flareSolverrSessionTtl: MutableStateFlow<Int> by OverrideConfigValue()
    val flareSolverrAsResponseFallback: MutableStateFlow<Boolean> by OverrideConfigValue()

    // opds settings
    val opdsUseBinaryFileSizes: MutableStateFlow<Boolean> by OverrideConfigValue()
    val opdsItemsPerPage: MutableStateFlow<Int> by OverrideConfigValue()
    val opdsEnablePageReadProgress: MutableStateFlow<Boolean> by OverrideConfigValue()
    val opdsMarkAsReadOnDownload: MutableStateFlow<Boolean> by OverrideConfigValue()
    val opdsShowOnlyUnreadChapters: MutableStateFlow<Boolean> by OverrideConfigValue()
    val opdsShowOnlyDownloadedChapters: MutableStateFlow<Boolean> by OverrideConfigValue()
    val opdsChapterSortOrder: MutableStateFlow<SortOrder> by OverrideConfigValue()

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
