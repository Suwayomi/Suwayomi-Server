package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

interface ISetting<Type> {
    val value: Type
    val requiresRestart: Boolean?
}

data class PartialSetting<Type>(
    override val value: Type,
    override val requiresRestart: Boolean? = null
) : ISetting<Type>

data class Setting<Type>(
    override val value: Type,
    override val requiresRestart: Boolean
) : ISetting<Type>

interface IServerSettings {
    val ip: ISetting<String>?
    val port: ISetting<Int>?
    val socksProxyEnabled: ISetting<Boolean>?
    val socksProxyHost: ISetting<String>?
    val socksProxyPort: ISetting<String>?
    val webUIEnabled: ISetting<Boolean>?
    val webUIFlavor: ISetting<String>?
    val initialOpenInBrowserEnabled: ISetting<Boolean>?
    val webUIInterface: ISetting<String>?
    val electronPath: ISetting<String>?
    val downloadAsCbz: ISetting<Boolean>?
    val downloadsPath: ISetting<String>?
    val maxParallelUpdateRequests: ISetting<Int>?
    val basicAuthEnabled: ISetting<Boolean>?
    val basicAuthUsername: ISetting<String>?
    val basicAuthPassword: ISetting<String>?
    val debugLogsEnabled: ISetting<Boolean>?
    val systemTrayEnabled: ISetting<Boolean>?
}

data class PartialServerSettings(
    override val ip: PartialSetting<String>? = null,
    override val port: PartialSetting<Int>? = null,
    override val socksProxyEnabled: PartialSetting<Boolean>? = null,
    override val socksProxyHost: PartialSetting<String>? = null,
    override val socksProxyPort: PartialSetting<String>? = null,
    override val webUIEnabled: PartialSetting<Boolean>? = null,
    override val webUIFlavor: PartialSetting<String>? = null,
    override val initialOpenInBrowserEnabled: PartialSetting<Boolean>? = null,
    override val webUIInterface: PartialSetting<String>? = null,
    override val electronPath: PartialSetting<String>? = null,
    override val downloadAsCbz: PartialSetting<Boolean>? = null,
    override val downloadsPath: PartialSetting<String>? = null,
    override val maxParallelUpdateRequests: PartialSetting<Int>? = null,
    override val basicAuthEnabled: PartialSetting<Boolean>? = null,
    override val basicAuthUsername: PartialSetting<String>? = null,
    override val basicAuthPassword: PartialSetting<String>? = null,
    override val debugLogsEnabled: PartialSetting<Boolean>? = null,
    override val systemTrayEnabled: PartialSetting<Boolean>? = null
) : IServerSettings

data class ServerSettings(
    override val ip: Setting<String>,
    override val port: Setting<Int>,
    override val socksProxyEnabled: Setting<Boolean>,
    override val socksProxyHost: Setting<String>,
    override val socksProxyPort: Setting<String>,
    override val webUIEnabled: Setting<Boolean>,
    override val webUIFlavor: Setting<String>,
    override val initialOpenInBrowserEnabled: Setting<Boolean>,
    override val webUIInterface: Setting<String>,
    override val electronPath: Setting<String>,
    override val downloadAsCbz: Setting<Boolean>,
    override val downloadsPath: Setting<String>,
    override val maxParallelUpdateRequests: Setting<Int>,
    override val basicAuthEnabled: Setting<Boolean>,
    override val basicAuthUsername: Setting<String>,
    override val basicAuthPassword: Setting<String>,
    override val debugLogsEnabled: Setting<Boolean>,
    override val systemTrayEnabled: Setting<Boolean>
) : IServerSettings

val DEFAULT_SETTINGS = ServerSettings(
    ip = Setting(value = "0.0.0.0", requiresRestart = true),
    port = Setting(value = 4567, requiresRestart = true),
    socksProxyEnabled = Setting(value = false, requiresRestart = true),
    socksProxyHost = Setting(value = "", requiresRestart = true),
    socksProxyPort = Setting(value = "", requiresRestart = true),
    webUIEnabled = Setting(value = true, requiresRestart = true),
    webUIFlavor = Setting(value = "WebUI", requiresRestart = true),
    initialOpenInBrowserEnabled = Setting(value = true, requiresRestart = true),
    webUIInterface = Setting(value = "browser", requiresRestart = true),
    electronPath = Setting(value = "", requiresRestart = true),
    downloadAsCbz = Setting(value = false, requiresRestart = true),
    downloadsPath = Setting(value = "", requiresRestart = true),
    maxParallelUpdateRequests = Setting(value = 10, requiresRestart = true),
    basicAuthEnabled = Setting(value = false, requiresRestart = true),
    basicAuthUsername = Setting(value = "", requiresRestart = true),
    basicAuthPassword = Setting(value = "", requiresRestart = true),
    debugLogsEnabled = Setting(value = false, requiresRestart = true),
    systemTrayEnabled = Setting(value = false, requiresRestart = true)
)

object Settings {
    private var cache: Map<String, Setting<String>> = emptyMap()

    fun init() {
        updateCache()
    }

    private fun updateCache() {
        cache = getAll()
    }

    fun getAll(): Map<String, Setting<String>> {
        return transaction {
            SettingsTable.selectAll().associate { it[SettingsTable.key] to SettingsTable.toDataClass(it) }
        }
    }

    fun get(key: String): Setting<String> {
        return cache[key] ?: throw IllegalArgumentException()
    }

    fun <T> put(key: String, setting: ISetting<T>) {
        transaction {
            SettingsTable.update({ SettingsTable.key eq key }) {
                it[value] = setting.value.toString()
                if (setting.requiresRestart != null) it[requiresRestart] = setting.requiresRestart!!
            }

            updateCache()
        }
    }

    private fun insertAndOrUpdate(settings: IServerSettings, doUpdate: Boolean = false) {
        @Suppress("UNCHECKED_CAST")
        fun <R> readInstanceProperty(instance: Any, propertyName: String): R {
            val property = instance::class.members
                // don't cast here to <Any, R>, it would succeed silently
                .first { it.name == propertyName } as KProperty1<Any, *>
            // force a invalid cast exception if incorrect type here
            return property.get(instance) as R
        }

        val settingsInDb = cache

        settings::class.memberProperties.forEach {
            val setting = readInstanceProperty<ISetting<Any>?>(settings, it.name) ?: return@forEach
            val settingInDb = settingsInDb[it.name]
            val isSettingMissing = settingInDb == null
            val updateRequired =
                setting.value.toString() != settingInDb?.value ||
                    setting.requiresRestart != null && setting.requiresRestart != settingInDb?.requiresRestart

            val shouldDoUpdate = !isSettingMissing && doUpdate && updateRequired

            if (shouldDoUpdate || isSettingMissing) {
                KotlinLogging.logger { }
                    .debug { "Setting \"${it.name}\" - insert= $isSettingMissing, update= $shouldDoUpdate - $setting" }
            }

            if (shouldDoUpdate) {
                put(it.name, setting)
                return@forEach
            }

            if (!isSettingMissing) {
                return@forEach
            }

            transaction {
                SettingsTable.insert { table ->
                    table[key] = it.name
                    table[value] = setting.value.toString()
                    if (setting.requiresRestart != null) table[requiresRestart] = setting.requiresRestart!!
                }
            }
        }

        updateCache()
    }

    fun reset() {
        insertAndOrUpdate(DEFAULT_SETTINGS, true)
    }

    fun setup() {
        insertAndOrUpdate(DEFAULT_SETTINGS, false)
    }

    fun update(settings: IServerSettings) {
        insertAndOrUpdate(settings, true)
    }

    fun fromConfig(config: ServerConfig): IServerSettings {
        return PartialServerSettings(
            ip = PartialSetting(value = config.ip),
            port = PartialSetting(value = config.port),
            socksProxyEnabled = PartialSetting(value = config.socksProxyEnabled),
            socksProxyHost = PartialSetting(value = config.socksProxyHost),
            socksProxyPort = PartialSetting(value = config.socksProxyPort),
            webUIEnabled = PartialSetting(value = config.webUIEnabled),
            webUIFlavor = PartialSetting(value = config.webUIFlavor),
            initialOpenInBrowserEnabled = PartialSetting(value = config.initialOpenInBrowserEnabled),
            webUIInterface = PartialSetting(value = config.webUIInterface),
            electronPath = PartialSetting(value = config.electronPath),
            downloadAsCbz = PartialSetting(value = config.downloadAsCbz),
            downloadsPath = PartialSetting(value = config.downloadsPath),
            maxParallelUpdateRequests = PartialSetting(value = config.maxParallelUpdateRequests),
            basicAuthEnabled = PartialSetting(value = config.basicAuthEnabled),
            basicAuthUsername = PartialSetting(value = config.basicAuthUsername),
            basicAuthPassword = PartialSetting(value = config.basicAuthPassword),
            debugLogsEnabled = PartialSetting(value = config.debugLogsEnabled),
            systemTrayEnabled = PartialSetting(value = config.systemTrayEnabled)
        )
    }
}
