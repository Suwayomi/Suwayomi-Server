package xyz.nulldev.ts.config.util

import com.typesafe.config.Config
import com.typesafe.config.ConfigException

operator fun Config.get(key: String) =
    try {
        // getString throws on a missing path - the Elvis the old code had was unreachable
        getString(key)
    } catch (e: ConfigException) {
        throw IllegalStateException("Could not find value for config entry: $key", e)
    }
