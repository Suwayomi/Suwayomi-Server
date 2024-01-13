package xyz.nulldev.ts.config

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.github.config4k.getValue
import kotlin.reflect.KProperty

/**
 * Abstract config module.
 */
@Suppress("UNUSED_PARAMETER")
abstract class ConfigModule(getConfig: () -> Config)

/**
 * Abstract jvm-commandline-argument-overridable config module.
 */
abstract class SystemPropertyOverridableConfigModule(getConfig: () -> Config, moduleName: String) : ConfigModule(getConfig) {
    val overridableConfig = SystemPropertyOverrideDelegate(getConfig, moduleName)
}

/** Defines a config property that is overridable with jvm `-D` commandline arguments prefixed with [CONFIG_PREFIX] */
class SystemPropertyOverrideDelegate(val getConfig: () -> Config, val moduleName: String) {
    inline operator fun <R, reified T> getValue(
        thisRef: R,
        property: KProperty<*>,
    ): T {
        val config = getConfig()
        val configValue: T = config.getValue(thisRef, property)

        val combined =
            System.getProperty(
                "$CONFIG_PREFIX.$moduleName.${property.name}",
                if (T::class.simpleName == "List") {
                    ConfigValueFactory.fromAnyRef(configValue).render()
                } else {
                    configValue.toString()
                },
            )

        return when (T::class.simpleName) {
            "Int" -> combined.toInt()
            "Boolean" -> combined.toBoolean()
            "Double" -> combined.toDouble()
            "List" -> ConfigFactory.parseString("internal=" + combined).getStringList("internal").orEmpty()
            // add more types as needed
            else -> combined // covers String
        } as T
    }
}
