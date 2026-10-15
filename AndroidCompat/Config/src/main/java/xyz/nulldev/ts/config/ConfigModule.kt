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
import com.typesafe.config.ConfigValueFactory
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
        val systemProperty =
            System.getProperty("$CONFIG_PREFIX.$moduleName.${property.name}")
        if (systemProperty == null) {
            val configValue: T = getConfig().getValue(thisRef, property)
            return configValue
        }

        val genericType = object : TypeReference<T>() {}.genericType()
        val clazz = ClassContainer(T::class, genericType)
        val reader = SelectReader.getReader(clazz)
        val path = property.name

        // System properties are interpreted as literal values first: embedding them into a
        // HOCON document would let `#`, `,` or `${...}` truncate/interpolate the value.
        // Only when the literal string cannot satisfy the reader (e.g. an Int reader) do we
        // fall back to HOCON semantics.
        val literalConfig =
            ConfigFactory
                .empty()
                .withValue("internal", ConfigValueFactory.fromAnyRef(systemProperty))
        val result =
            runCatching { reader(literalConfig, "internal") }.getOrElse {
                val systemPropertyConfig =
                    try {
                        ConfigFactory.parseString("internal=$systemProperty")
                    } catch (_: ConfigException) {
                        ConfigFactory.parseMap(mapOf("internal" to systemProperty))
                    }
                reader(systemPropertyConfig, "internal")
            }

        return try {
            result as T
        } catch (e: Exception) {
            throw ConfigException.BadValue(
                path,
                "system property '$CONFIG_PREFIX.$moduleName.${property.name}' is not a valid ${T::class.qualifiedName}",
                e,
            )
        }
    }
}
