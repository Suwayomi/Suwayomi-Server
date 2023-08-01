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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import xyz.nulldev.ts.config.SystemPropertyOverridableConfigModule
import kotlin.reflect.KProperty

val mutableConfigValueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private const val MODULE_NAME = "server"
class ServerConfig(getConfig: () -> Config, moduleName: String = MODULE_NAME) : SystemPropertyOverridableConfigModule(getConfig, moduleName) {
    inner class OverrideConfigValue<T>(private val configAdapter: ConfigAdapter<T>) {
        private var flow: MutableStateFlow<T>? = null

        operator fun getValue(thisRef: ServerConfig, property: KProperty<*>): MutableStateFlow<T> {
            if (flow != null) {
                return flow!!
            }

            val value = configAdapter.toType(overridableConfig.getValue<ServerConfig, String>(thisRef, property))

            val stateFlow = MutableStateFlow(value)
            flow = stateFlow

            stateFlow.drop(1).distinctUntilChanged().onEach { overridableConfig.setValue(thisRef, property, it as Any) }
                .launchIn(mutableConfigValueScope)

            return stateFlow
        }
    }

    val ip: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val port: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)

    // proxy
    val socksProxyEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val socksProxyHost: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val socksProxyPort: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)

    // webUI
    val webUIEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val webUIFlavor: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val initialOpenInBrowserEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val webUIInterface: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val electronPath: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val webUIChannel: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val webUIUpdateCheckInterval: MutableStateFlow<Double> by OverrideConfigValue(DoubleConfigAdapter)

    // downloader
    val downloadAsCbz: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val downloadsPath: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val autoDownloadNewChapters: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)

    // updater
    val maxParallelUpdateRequests: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)
    val excludeUnreadChapters: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val excludeNotStarted: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val excludeCompleted: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val globalUpdateInterval: MutableStateFlow<Double> by OverrideConfigValue(DoubleConfigAdapter)

    // Authentication
    val basicAuthEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val basicAuthUsername: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val basicAuthPassword: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)

    // misc
    val debugLogsEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)
    val systemTrayEnabled: MutableStateFlow<Boolean> by OverrideConfigValue(BooleanConfigAdapter)

    // backup
    val backupPath: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val backupTime: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)
    val backupInterval: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)
    val backupTTL: MutableStateFlow<Int> by OverrideConfigValue(IntConfigAdapter)

    // local source
    val localSourcePath: MutableStateFlow<String> by OverrideConfigValue(StringConfigAdapter)

    fun <T> subscribeTo(flow: Flow<T>, onChange: suspend (value: T) -> Unit, ignoreInitialValue: Boolean = true) {
        val actualFlow = if (ignoreInitialValue) {
            flow.drop(1)
        } else {
            flow
        }

        val sharedFlow = MutableSharedFlow<T>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        actualFlow.distinctUntilChanged().onEach { sharedFlow.emit(it) }.launchIn(mutableConfigValueScope)
        sharedFlow.onEach { onChange(it) }.launchIn(mutableConfigValueScope)
    }

    fun <T> subscribeTo(flow: Flow<T>, onChange: suspend () -> Unit, ignoreInitialValue: Boolean = true) {
        subscribeTo(flow, { _ -> onChange() }, ignoreInitialValue)
    }

    fun <T> subscribeTo(
        mutableStateFlow: MutableStateFlow<T>,
        onChange: suspend (value: T) -> Unit,
        ignoreInitialValue: Boolean = true
    ) {
        subscribeTo(mutableStateFlow.asStateFlow(), onChange, ignoreInitialValue)
    }

    fun <T> subscribeTo(
        mutableStateFlow: MutableStateFlow<T>,
        onChange: suspend () -> Unit,
        ignoreInitialValue: Boolean = true
    ) {
        subscribeTo(mutableStateFlow.asStateFlow(), { _ -> onChange() }, ignoreInitialValue)
    }

    companion object {
        fun register(getConfig: () -> Config) = ServerConfig({ getConfig().getConfig(MODULE_NAME) })
    }
}
