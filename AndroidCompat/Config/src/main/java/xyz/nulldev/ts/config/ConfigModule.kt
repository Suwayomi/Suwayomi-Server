package xyz.nulldev.ts.config

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.github.config4k.ClassContainer
import io.github.config4k.TypeReference
import io.github.config4k.getValue
import io.github.config4k.readers.SelectReader
import kotlin.reflect.KProperty

/**
 * Abstract config module.
 */
@Suppress("UNUSED_PARAMETER")
abstract class ConfigModule(
    getConfig: () -> Config,
)

/**
 * Abstract jvm-commandline-argument-overridable config module.
 */
abstract class SystemPropertyOverridableConfigModule(
    getConfig: () -> Config,
    val moduleName: String,
) : ConfigModule(getConfig) {
    val overridableConfig = SystemPropertyOverrideDelegate(getConfig, moduleName)
}

/** Defines a config property that is overridable with jvm `-D` commandline arguments prefixed with [CONFIG_PREFIX] */
class SystemPropertyOverrideDelegate(
    val getConfig: () -> Config,
    val moduleName: String,
) {
    inline operator fun <R, reified T> getValue(
        thisRef: R,
        property: KProperty<*>,
    ): T {
        val config = getConfig()

        val systemProperty =
            System.getProperty("$CONFIG_PREFIX.$moduleName.${property.name}")
        if (systemProperty == null) {
            val configValue: T = config.getValue(thisRef, property)
            return configValue
        }

        val systemPropertyConfig =
            try {
                ConfigFactory.parseString("internal=$systemProperty")
            } catch (_: ConfigException) {
                ConfigFactory.parseMap(mapOf("internal" to systemProperty))
            }

        val genericType = object : TypeReference<T>() {}.genericType()
        val clazz = ClassContainer(T::class, genericType)
        val reader = SelectReader.getReader(clazz)
        val path = property.name

        val result = reader(systemPropertyConfig, "internal")
        return try {
            result as T
        } catch (e: Exception) {
            throw result
                ?.let { e }
                ?: ConfigException.BadPath(path, "take a look at your config")
        }
    }
}
