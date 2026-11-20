package xyz.nulldev.ts.config.util

import com.typesafe.config.Config

operator fun Config.get(key: String) =
    getString(key)
        ?: throw IllegalStateException("Could not find value for config entry: $key!")
