package ir.armor.tachidesk

import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import mu.KotlinLogging
import org.slf4j.Logger
import java.io.File

/**
 * Load configs
 */
fun loadConfigs(dataRoot: String): Config {
    val logger = KotlinLogging.logger {}
    //Load reference configs
    val compatConfig =  ConfigFactory.parseResources("compat-reference.conf")
    val serverConfig = ConfigFactory.parseResources("server-reference.conf")

    //Load user config
    val userConfig =
        File(dataRoot, "server.conf").let {
            ConfigFactory.parseFile(it)
        }

    val config = ConfigFactory.empty()
        .withFallback(userConfig)
        .withFallback(compatConfig)
        .withFallback(serverConfig)
        .resolve()

    logger.debug {
        "Loaded config:\n" + config.root().render(ConfigRenderOptions.concise().setFormatted(true))
    }

    return config
}

fun setLoggingEnabled(enabled: Boolean = true) {
    val logger = (KotlinLogging.logger(Logger.ROOT_LOGGER_NAME).underlyingLogger as ch.qos.logback.classic.Logger)
    logger.level = if (enabled) {
        Level.DEBUG
    } else Level.ERROR
}