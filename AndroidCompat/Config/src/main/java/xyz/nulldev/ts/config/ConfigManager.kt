package xyz.nulldev.ts.config

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import mu.KotlinLogging
import java.io.File

/**
 * Manages app config.
 */
open class ConfigManager {
    private val generatedModules = mutableMapOf<Class<out ConfigModule>, ConfigModule>()
    val config by lazy { loadConfigs() }

    //Public read-only view of modules
    val loadedModules: Map<Class<out ConfigModule>, ConfigModule>
        get() = generatedModules

    val logger = KotlinLogging.logger {}

    /**
     * Get a config module
     */
    inline fun <reified T : ConfigModule> module(): T = loadedModules[T::class.java] as T

    /**
     * Get a config module (Java API)
     */
    fun <T : ConfigModule> module(type: Class<T>): T = loadedModules[type] as T

    /**
     * Load configs
     */
    fun loadConfigs(): Config {
        //Load reference configs
        val compatConfig = ConfigFactory.parseResources("compat-reference.conf")
        val serverConfig = ConfigFactory.parseResources("server-reference.conf")
        val baseConfig =
                ConfigFactory.parseMap(
                        mapOf(
                                "ts.server.rootDir" to ApplicationRootDir
                        )
                )

        //Load user config
        val userConfig =
                File(ApplicationRootDir, "server.conf").let {
                    ConfigFactory.parseFile(it)
                }


        val config = ConfigFactory.empty()
                .withFallback(baseConfig)
                .withFallback(userConfig)
                .withFallback(compatConfig)
                .withFallback(serverConfig)
                .resolve()

        // set log level early
        if (debugLogsEnabled(config)) {
            setLogLevel(Level.DEBUG)
        }

        logger.debug {
            "Loaded config:\n" + config.root().render(ConfigRenderOptions.concise().setFormatted(true))
        }

        return config
    }

    fun registerModule(module: ConfigModule) {
        generatedModules[module.javaClass] = module
    }

    fun registerModules(vararg modules: ConfigModule) {
        modules.forEach {
            registerModule(it)
        }
    }
}

object GlobalConfigManager : ConfigManager()
