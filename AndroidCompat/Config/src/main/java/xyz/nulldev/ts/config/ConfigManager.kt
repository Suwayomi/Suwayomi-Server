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
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue
import com.typesafe.config.parser.ConfigDocument
import com.typesafe.config.parser.ConfigDocumentFactory
import io.github.config4k.toConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val mutex = Mutex()

    /**
     * Get a config module
     */
    inline fun <reified T : ConfigModule> module(): T = loadedModules[T::class.java] as T

    /**
     * Get a config module (Java API)
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : ConfigModule> module(type: Class<T>): T = loadedModules[type] as T

    private fun getUserConfig(): Config =
        userConfigFile.let {
            ConfigFactory.parseFile(it)
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
                    // override AndroidCompat's rootDir
                    "androidcompat.rootDir" to "$ApplicationRootDir/android-compat",
                ),
            )

        // Load user config
        val userConfig = getUserConfig()

        val config =
            ConfigFactory
                .empty()
                .withFallback(baseConfig)
                .withFallback(userConfig)
                .withFallback(compatConfig)
                .withFallback(serverConfig)
                .resolve()

        // set log level early
        if (debugLogsEnabled(config)) {
            setLogLevelFor(BASE_LOGGER_NAME, Level.DEBUG)
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

    private fun updateUserConfigFile(
        path: String,
        value: ConfigValue,
    ) {
        val userConfigDoc = ConfigDocumentFactory.parseFile(userConfigFile)
        val updatedConfigDoc = userConfigDoc.withValue(path, value)
        val newFileContent = updatedConfigDoc.render()
        userConfigFile.writeText(newFileContent)
    }

    suspend fun updateValue(
        path: String,
        value: Any,
    ) {
        mutex.withLock {
            val actualValue = if (value is Enum<*>) value.name else value
            val configValue = actualValue.toConfig("internal").getValue("internal")

            updateUserConfigFile(path, configValue)
            internalConfig = internalConfig.withValue(path, configValue)
        }
    }

    fun resetUserConfig(updateInternalConfig: Boolean = true): ConfigDocument {
        val serverConfigFileContent = this::class.java.getResource("/server-reference.conf")?.readText()
        val serverConfigDoc = ConfigDocumentFactory.parseString(serverConfigFileContent)
        userConfigFile.writeText(serverConfigDoc.render())

        if (updateInternalConfig) {
            getUserConfig().entrySet().forEach { internalConfig = internalConfig.withValue(it.key, it.value) }
        }

        return serverConfigDoc
    }

    /**
     * Makes sure the "UserConfig" is up-to-date.
     *
     *  - adds missing settings
     *  - removes outdated settings
     */
    fun updateUserConfig(migrate: ConfigDocument.(Config) -> ConfigDocument) {
        val serverConfig = ConfigFactory.parseResources("server-reference.conf")
        val userConfig = getUserConfig()

        // NOTE: if more than 1 dot is included, that's a nested setting, which we need to filter out here
        val refKeys =
            serverConfig.root().entries.flatMap {
                (it.value as? ConfigObject)?.entries?.map { e -> "${it.key}.${e.key}" }.orEmpty()
            }
        val hasMissingSettings = refKeys.any { !userConfig.hasPath(it) }
        val hasOutdatedSettings = userConfig.entrySet().any { !refKeys.contains(it.key) && it.key.count { c -> c == '.' } <= 1 }
        val isUserConfigOutdated = hasMissingSettings || hasOutdatedSettings
        if (!isUserConfigOutdated) {
            return
        }

        logger.debug {
            "user config is out of date, updating... (missingSettings= $hasMissingSettings, outdatedSettings= $hasOutdatedSettings"
        }

        var newUserConfigDoc: ConfigDocument = resetUserConfig(false)
        userConfig
            .entrySet()
            .filter {
                serverConfig.hasPath(
                    it.key,
                ) ||
                    it.key.count { c -> c == '.' } > 1
            }.forEach { newUserConfigDoc = newUserConfigDoc.withValue(it.key, it.value) }

        newUserConfigDoc =
            migrate(newUserConfigDoc, internalConfig)

        userConfigFile.writeText(newUserConfigDoc.render())
        getUserConfig().entrySet().forEach { internalConfig = internalConfig.withValue(it.key, it.value) }
    }
}

object GlobalConfigManager : ConfigManager()
