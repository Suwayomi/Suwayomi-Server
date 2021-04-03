package ir.armor.tachidesk

import ch.qos.logback.classic.Level
import mu.KotlinLogging
import org.slf4j.Logger

fun setLoggingEnabled(enabled: Boolean = true) {
    val logger = (KotlinLogging.logger(Logger.ROOT_LOGGER_NAME).underlyingLogger as ch.qos.logback.classic.Logger)
    logger.level = if (enabled) {
        Level.DEBUG
    } else Level.ERROR
}
