package xyz.nulldev.ts.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import mu.KotlinLogging
import java.io.File

/**
 * Manages app config.
 */
open class ConfigManager {
    private val generatedModules
            = mutableMapOf<Class<out ConfigModule>, ConfigModule>()
    val config by lazy { loadConfigs() }

    //Public read-only view of modules
    val loadedModules: Map<Class<out ConfigModule>, ConfigModule>
        get() = generatedModules

    open val configFolder: String
        get() = System.getProperty("compat-configdirs") ?: "tachiserver-data/config"

    val logger = KotlinLogging.logger {}

    /**
     * Get a config module
     */
    inline fun <reified T : ConfigModule> module(): T
            = loadedModules[T::class.java] as T

    /**
     * Get a config module (Java API)
     */
    fun <T : ConfigModule> module(type: Class<T>): T
            = loadedModules[type] as T

    /**
     * Load configs
     */
    fun loadConfigs(): Config {
        val configs = mutableListOf<Config>()

        //Load reference config
        configs += ConfigFactory.parseResources("reference.conf")

        //Load custom configs from dir
        File(configFolder).listFiles()?.map {
            ConfigFactory.parseFile(it)
        }?.filterNotNull()?.forEach {
            configs += it.withFallback(configs.last())
        }

        val config = configs.last().resolve()

        logger.debug {
            "Loaded config:\n" + config.root().render(ConfigRenderOptions.concise().setFormatted(true))
        }

        return config
    }

    fun registerModule(module: ConfigModule) {
        generatedModules.put(module.javaClass, module)
    }

    fun registerModules(vararg modules: ConfigModule) {
        modules.forEach {
            registerModule(it)
        }
    }
}

object GlobalConfigManager : ConfigManager()
