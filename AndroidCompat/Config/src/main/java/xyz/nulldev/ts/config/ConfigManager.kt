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
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manages app config.
 */
open class ConfigManager {
    val logger = KotlinLogging.logger {}
    private val generatedModules = mutableMapOf<Class<out ConfigModule>, ConfigModule>()
    private val userConfigFile = File(ApplicationRootDir, "server.conf")

    @Volatile
    private var internalConfig = loadConfigs()
    val config: Config
        get() = internalConfig

    // Public read-only snapshot of modules
    val loadedModules: Map<Class<out ConfigModule>, ConfigModule>
        get() = generatedModules.toMap()

    /**
     * Serializes every write path to `server.conf` plus the corresponding [internalConfig]
     * update. Not a coroutine `Mutex`: callers like the setting delegates' initial
     * correction write-back are not suspendable.
     */
    private val configLock = ReentrantLock()

    /**
     * Get a config module
     */
    inline fun <reified T : ConfigModule> module(): T = module(T::class.java)

    /**
     * Get a config module (Java API)
     */
    fun <T : ConfigModule> module(type: Class<T>): T =
        loadedModules[type] as? T
            ?: error("ConfigModule ${type.name} is not registered")

    private fun getUserConfig(): Config =
        userConfigFile.let {
            ConfigFactory.parseFile(it)
        }

    /**
     * Write `content` to `file` atomically (tmp file + move) so a crash mid-write cannot
     * leave a truncated `server.conf` behind.
     */
    private fun writeAtomically(
        file: File,
        content: String,
    ) {
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        // the move installs a fresh inode created with umask defaults - carry over the target
        // file's permissions first (e.g. a chmod 600 server.conf holding credentials) so the
        // first write does not silently widen access
        val existingPermissions =
            runCatching { Files.getPosixFilePermissions(file.toPath()) }.getOrNull()
        tmpFile.writeText(content)
        if (existingPermissions != null) {
            runCatching { Files.setPosixFilePermissions(tmpFile.toPath(), existingPermissions) }
                .onFailure { logger.warn(it) { "Failed to preserve permissions of ${file.name}" } }
        }
        try {
            Files.move(
                tmpFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // filesystem does not support atomic moves - fall back to a plain replace
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Load configs
     */
    private fun loadConfigs(): Config {
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
        writeAtomically(userConfigFile, newFileContent)
    }

    fun updateValue(
        path: String,
        value: Any,
    ) {
        configLock.withLock {
            val configValue = value.toConfig("internal").getValue("internal")

            updateUserConfigFile(path, configValue)
            internalConfig = internalConfig.withValue(path, configValue)
        }
    }

    private fun createConfigDocumentFromReference(): ConfigDocument {
        val serverConfigFileContent = this::class.java.getResource("/server-reference.conf")?.readText()
        return ConfigDocumentFactory.parseString(serverConfigFileContent)
    }

    fun resetUserConfig(): ConfigDocument =
        configLock.withLock {
            val serverConfigDoc = createConfigDocumentFromReference()

            writeAtomically(userConfigFile, serverConfigDoc.render())
            // full rebuild so keys deleted from the user config don't linger in memory
            internalConfig = loadConfigs()

            serverConfigDoc
        }

    /**
     * Makes sure the "UserConfig" is up-to-date.
     *
     *  - Adds missing settings
     *  - Migrates deprecated settings
     *  - Removes outdated settings
     */
    fun updateUserConfig(migrate: ConfigDocument.(Config) -> ConfigDocument): Unit =
        configLock.withLock {
            val serverConfig = ConfigFactory.parseResources("server-reference.conf")
            val userConfig = getUserConfig()

            // NOTE: if more than 1 dot is included, that's a nested setting, which we need to filter out here
            val refKeys =
                serverConfig.root().entries.flatMap {
                    (it.value as? ConfigObject)?.entries?.map { e -> "${it.key}.${e.key}" }.orEmpty()
                }
            val hasMissingSettings = refKeys.any { !userConfig.hasPath(it) }
            // only `server.*` keys are ours to garbage-collect; foreign namespaces like
            // `androidcompat.rootDir` (or a user's own top-level keys) must be preserved
            val hasOutdatedSettings =
                userConfig.entrySet().any {
                    it.key.startsWith("server.") && it.key.count { c -> c == '.' } <= 1 && !refKeys.contains(it.key)
                }

            val isUserConfigOutdated = hasMissingSettings || hasOutdatedSettings
            if (!isUserConfigOutdated) {
                return@withLock
            }

            logger.debug {
                "user config is out of date, updating... (missingSettings= $hasMissingSettings, outdatedSettings= $hasOutdatedSettings)"
            }

            var newUserConfigDoc: ConfigDocument = createConfigDocumentFromReference()
            userConfig
                .entrySet()
                .filter {
                    serverConfig.hasPath(it.key) ||
                        !it.key.startsWith("server.") ||
                        it.key.count { c -> c == '.' } > 1
                }.forEach { newUserConfigDoc = newUserConfigDoc.withValue(it.key, it.value) }

            // the migration sees the raw user config so it can tell "explicitly set by the
            // user" apart from "inherited from the reference config"
            newUserConfigDoc =
                migrate(newUserConfigDoc, userConfig)

            writeAtomically(userConfigFile, newUserConfigDoc.render())
            // full rebuild so keys deleted from the user config don't linger in memory
            internalConfig = loadConfigs()
        }

    fun getRedactedConfig(nonPrivacySafeKeys: List<String>): Config {
        val entries =
            config.entrySet().associate { entry ->
                val key = entry.key
                val value =
                    if (nonPrivacySafeKeys.any { "server.$it" == key }) {
                        "[REDACTED]"
                    } else {
                        entry.value.unwrapped()
                    }

                key to value
            }

        return ConfigFactory.parseMap(entries)
    }
}

object GlobalConfigManager : ConfigManager()
