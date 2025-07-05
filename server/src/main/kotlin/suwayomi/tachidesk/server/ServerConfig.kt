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

val mutableConfigValueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

const val SERVER_CONFIG_MODULE_NAME = "server"

class ServerConfig(
    getConfig: () -> Config,
    val moduleName: String = SERVER_CONFIG_MODULE_NAME,
) : SystemPropertyOverridableConfigModule(
        getConfig,
        moduleName,
    ) {
    open inner class OverrideConfigValue<T>(
        private val configAdapter: ConfigAdapter<out Any>,
    ) {
        private var flow: MutableStateFlow<T>? = null

        open fun getValueFromConfig(
            thisRef: ServerConfig,
            property: KProperty<*>,
        ): Any = configAdapter.toType(overridableConfig.getValue<ServerConfig, String>(thisRef, property))

        operator fun getValue(
            thisRef: ServerConfig,
            property: KProperty<*>,
        ): MutableStateFlow<T> {
            if (flow != null) {
                return flow!!
            }

            @Suppress("UNCHECKED_CAST")
            val value = getValueFromConfig(thisRef, property) as T

            val stateFlow = MutableStateFlow(value)
            flow = stateFlow

            stateFlow
                .drop(1)
                .distinctUntilChanged()
                .filter { it != getValueFromConfig(thisRef, property) }
                .onEach { GlobalConfigManager.updateValue("$moduleName.${property.name}", it as Any) }
                .launchIn(mutableConfigValueScope)

            return stateFlow
        }
    }

    inner class OverrideConfigValues<T>(
        private val configAdapter: ConfigAdapter<out Any>,
    ) : OverrideConfigValue<T>(configAdapter) {
        override fun getValueFromConfig(
            thisRef: ServerConfig,
            property: KProperty<*>,
        ): Any =
            overridableConfig
                .getValue<ServerConfig, List<String>>(thisRef, property)
                .map { configAdapter.toType(it) }
    }

    open inner class MigratedConfigValue<T>(
        private val readMigrated: () -> Any,
        private val setMigrated: (T) -> Unit,
    ) {
        private var flow: MutableStateFlow<T>? = null

        open fun getValueFromConfig(
            thisRef: ServerConfig,
            property: KProperty<*>,
        ): Any = readMigrated()

        operator fun getValue(
            thisRef: ServerConfig,
            property: KProperty<*>,
        ): MutableStateFlow<T> {
            if (flow != null) {
                return flow!!
            }

            @Suppress("UNCHECKED_CAST")
            val value = getValueFromConfig(thisRef, property) as T

            val stateFlow = MutableStateFlow(value)
            flow = stateFlow

            stateFlow
                .drop(1)
                .distinctUntilChanged()
                .filter { it != getValueFromConfig(thisRef, property) }
                .onEach(setMigrated)
                .launchIn(mutableConfigValueScope)

            return stateFlow
        }
    }

    val ip: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val port: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)

    // proxy
    val socksProxyEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val socksProxyVersion: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)
    val socksProxyHost: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val socksProxyPort: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val socksProxyUsername: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val socksProxyPassword: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)

    // webUI
    val webUIEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val webUIFlavor: MutableStateFlow<WebUIFlavor> by OverrideConfigValue(EnumConfigAdapter(WebUIFlavor::class.java))
    val initialOpenInBrowserEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val webUIInterface: MutableStateFlow<WebUIInterface> by OverrideConfigValue(EnumConfigAdapter(WebUIInterface::class.java))
    val electronPath: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val webUIChannel: MutableStateFlow<WebUIChannel> by OverrideConfigValue(EnumConfigAdapter(WebUIChannel::class.java))
    val webUIUpdateCheckInterval: MutableStateFlow<Double> by OverrideConfigValue(DoubleConfigAdapter)

    // downloader
    val downloadAsCbz: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val downloadsPath: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val autoDownloadNewChapters: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val excludeEntryWithUnreadChapters: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val autoDownloadNewChaptersLimit: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)
    val autoDownloadIgnoreReUploads: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)

    // extensions
    val extensionRepos: MutableStateFlow<List<String>> by OverrideConfigValues(StringConfigAdapter)

    // playwright webview
    val playwrightBrowser: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val playwrightWsEndpoint: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val playwrightSandbox: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)

    // webview
    val webviewImpl: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)

    // requests
    val maxSourcesInParallel: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)

    // updater
    val excludeUnreadChapters: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val excludeNotStarted: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val excludeCompleted: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val globalUpdateInterval: MutableStateFlow<Double> by OverrideConfigValue(DoubleConfigAdapter)
    val updateMangas: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)

    // Authentication
    val authMode: MutableStateFlow<AuthMode> by OverrideConfigValue(EnumConfigAdapter(AuthMode::class.java))
    val authUsername: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val authPassword: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
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
    val debugLogsEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val systemTrayEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val maxLogFiles: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)
    val maxLogFileSize: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val maxLogFolderSize: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)

    // backup
    val backupPath: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val backupTime: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val backupInterval: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)
    val backupTTL: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)

    // local source
    val localSourcePath: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)

    // cloudflare bypass
    val flareSolverrEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val flareSolverrUrl: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val flareSolverrTimeout: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)
    val flareSolverrSessionName: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val flareSolverrSessionTtl: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)
    val flareSolverrAsResponseFallback: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)

    // opds settings
    val opdsUseBinaryFileSizes: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val opdsItemsPerPage: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)
    val opdsEnablePageReadProgress: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val opdsMarkAsReadOnDownload: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val opdsShowOnlyUnreadChapters: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val opdsShowOnlyDownloadedChapters: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val opdsChapterSortOrder: MutableStateFlow<SortOrder> by OverrideConfigValue(EnumConfigAdapter(SortOrder::class.java))

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
        fun register(getConfig: () -> Config) = ServerConfig({ getConfig().getConfig(SERVER_CONFIG_MODULE_NAME) })
    }
}
