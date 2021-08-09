package xyz.nulldev.ts.config

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.typesafe.config.Config
import io.github.config4k.getValue
import kotlin.reflect.KProperty

/**
 * Abstract config module.
 */
abstract class ConfigModule(config: Config, moduleName: String = "") {
    val overridableWithSysProperty = SystemPropertyOverrideDelegate(config, moduleName)
}

class SystemPropertyOverrideDelegate(val config: Config, val moduleName: String) {
    inline operator fun <R, reified T> getValue(thisRef: R, property: KProperty<*>): T {
        val configValue: T = config.getValue(thisRef, property)

        val combined = System.getProperty(
            "suwayomi.tachidesk.config.$moduleName.${property.name}",
            configValue.toString()
        )

        val asT = when(T::class.simpleName) {
            "Int" -> combined.toInt()
            "Boolean" -> combined.toBoolean()
            // add more types as needed
            else -> combined
        }

        return asT as T
    }
}
