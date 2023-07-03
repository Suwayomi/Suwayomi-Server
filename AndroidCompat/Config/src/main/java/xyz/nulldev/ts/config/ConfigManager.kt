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
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.parser.ConfigDocument
import com.typesafe.config.parser.ConfigDocumentFactory
import mu.KotlinLogging
import java.io.File

/**
 * Manages app config.
 */
open class ConfigManager {
    val logger = KotlinLogging.logger {}
    private val generatedModules = mutableMapOf<Class<out ConfigModule>, ConfigModule>()
    private val userConfigFile = File(ApplicationRootDir, "server.conf")
    private var internalConfig = loadConfigs()
    val config: Config
        get() = internalConfig

    // Public read-only view of modules
    val loadedModules: Map<Class<out ConfigModule>, ConfigModule>
        get() = generatedModules

    /**
     * Get a config module
     */
    inline fun <reified T : ConfigModule> module(): T = loadedModules[T::class.java] as T

    /**
     * Get a config module (Java API)
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : ConfigModule> module(type: Class<T>): T = loadedModules[type] as T

    private fun getUserConfig(): Config {
        return userConfigFile.let {
            ConfigFactory.parseFile(it)
        }
    }

    /**
     * Load configs
     */
    fun loadConfigs(): Config {
        // Load reference configs
        val compatConfig = ConfigFactory.parseResources("compat-reference.conf")
        val serverConfig = ConfigFactory.parseResources("server-reference.conf")
        val baseConfig =
            ConfigFactory.parseMap(
                mapOf(
                    "androidcompat.rootDir" to "$ApplicationRootDir/android-compat" // override AndroidCompat's rootDir
                )
            )

        // Load user config
        val userConfig = getUserConfig()

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

    private fun updateUserConfigFile(path: String, value: ConfigValue) {
        val userConfigDoc = ConfigDocumentFactory.parseFile(userConfigFile)
        val updatedConfigDoc = userConfigDoc.withValue(path, value)
        val newFileContent = updatedConfigDoc.render()
        userConfigFile.writeText(newFileContent)
    }

    fun updateValue(path: String, value: Any) {
        val configValue = ConfigValueFactory.fromAnyRef(value)

        updateUserConfigFile(path, configValue)
        internalConfig = internalConfig.withValue(path, configValue)
    }

    /**
     * Makes sure the "UserConfig" is up-to-date.
     *
     *  - adds missing settings
     *  - removes outdated settings
     */
    fun updateUserConfig() {
        val serverConfigFileContent = this::class.java.getResource("/server-reference.conf")?.readText()
        val serverConfig = ConfigFactory.parseResources("server-reference.conf")
        val userConfig = getUserConfig()

        val hasMissingSettings = serverConfig.entrySet().any { !userConfig.hasPath(it.key) }
        val hasOutdatedSettings = userConfig.entrySet().any { !serverConfig.hasPath(it.key) }
        val isUserConfigOutdated = hasMissingSettings || hasOutdatedSettings
        if (!isUserConfigOutdated) {
            return
        }

        logger.debug { "user config is out of date, updating... (missingSettings= $hasMissingSettings, outdatedSettings= $hasOutdatedSettings" }

        val serverConfigDoc = ConfigDocumentFactory.parseString(serverConfigFileContent)
        userConfigFile.writeText(serverConfigDoc.render())

        var newUserConfigDoc: ConfigDocument = serverConfigDoc
        userConfig.entrySet().filter { serverConfig.hasPath(it.key) }.forEach { newUserConfigDoc = newUserConfigDoc.withValue(it.key, it.value) }

        userConfigFile.writeText(newUserConfigDoc.render())
    }
}

object GlobalConfigManager : ConfigManager()
