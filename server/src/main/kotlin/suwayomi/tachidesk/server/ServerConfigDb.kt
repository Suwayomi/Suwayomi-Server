package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

abstract class DatabaseDelegatorModule {
    protected var databaseDelegator = DatabaseDelegator()
}

class DatabaseDelegator {
    operator fun <R, SettingType, SETTING : Setting<SettingType>> setValue(
        thisRef: R,
        property: KProperty<*>,
        setting: SETTING
    ) {
        Settings.put(property.name, setting)
    }

    inline operator fun <R, reified SETTING> getValue(thisRef: R, property: KProperty<*>): SETTING {
        @Suppress("UNCHECKED_CAST")
        val setting: Setting<Any> = try {
            Settings.get(property.name) as Setting<Any>
        } catch (e: Exception) {
            (DEFAULT_SETTINGS::class.members.first { it.name == property.name } as KProperty1<Any, *>).get(
                DEFAULT_SETTINGS
            ) as Setting<Any>
        }

        return when (property.returnType.arguments[0].type?.classifier) {
            Boolean::class -> Setting(
                value = setting.value.toString().toBoolean(),
                requiresRestart = setting.requiresRestart
            )

            Int::class -> Setting(value = setting.value.toString().toInt(), requiresRestart = setting.requiresRestart)
            else -> Setting(value = setting.value.toString(), requiresRestart = setting.requiresRestart)
        } as SETTING
    }
}

class ServerConfigDb : DatabaseDelegatorModule(), IServerSettings {
    // Server ip and port bindings
    override var ip: Setting<String> by databaseDelegator
    override val port: Setting<Int> by databaseDelegator

    // proxy
    override val socksProxyEnabled: Setting<Boolean> by databaseDelegator
    override val socksProxyHost: Setting<String> by databaseDelegator
    override val socksProxyPort: Setting<String> by databaseDelegator

    // webUI
    override val webUIEnabled: Setting<Boolean> by databaseDelegator
    override val webUIFlavor: Setting<String> by databaseDelegator
    override val initialOpenInBrowserEnabled: Setting<Boolean> by databaseDelegator
    override val webUIInterface: Setting<String> by databaseDelegator
    override val electronPath: Setting<String> by databaseDelegator

    // downloader
    override val downloadAsCbz: Setting<Boolean> by databaseDelegator
    override val downloadsPath: Setting<String> by databaseDelegator

    // updater
    override val maxParallelUpdateRequests: Setting<Int> by databaseDelegator

    // Authentication
    override val basicAuthEnabled: Setting<Boolean> by databaseDelegator
    override val basicAuthUsername: Setting<String> by databaseDelegator
    override val basicAuthPassword: Setting<String> by databaseDelegator

    // misc
    override val debugLogsEnabled: Setting<Boolean> by databaseDelegator
    override val systemTrayEnabled: Setting<Boolean> by databaseDelegator
}
