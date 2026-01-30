/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ireader.core.log


import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Logging utility using Kermit for Kotlin Multiplatform.
 *
 * Kermit provides:
 * - Multiplatform support (Android, iOS, JVM, JS)
 * - Crashlytics integration via CrashlyticsLogWriter
 * - Configurable log levels
 * - Tag support
 */
object Log {


    private val logger = KotlinLogging.logger {}

    private const val DEFAULT_TAG = "IReader"


    /**
     * Enable verbose logging (Debug and Info levels).
     * Call this during development or when debugging is needed.
     */
    fun enableVerboseLogging() {

    }

    /**
     * Enable production logging (only Warn and Error levels).
     * Call this for release builds to reduce log noise.
     */
    fun enableProductionLogging() {

    }

    /**
     * Logs a lazy message at verbose level.
     */
    fun verbose(message: () -> String) {
        logger.warn (message);
    }

    /**
     * Logs a formatted message at verbose level.
     */
    fun verbose(message: String, vararg arguments: Any?) {
        logger.warn { message }
    }

    /**
     * Logs an exception at verbose level.
     */
    fun verbose(exception: Throwable, message: String? = null, vararg arguments: Any?) {
        logger.warn { message }
    }

    /**
     * Logs a lazy message at debug level.
     */
    fun debug(message: () -> String) {
        logger.warn { message }
    }

    /**
     * Logs a formatted message at debug level.
     */
    fun debug(message: String, vararg arguments: Any?) {
        logger.debug { message }
    }

    /**
     * Logs an exception at debug level.
     */
    fun debug(exception: Throwable, message: String? = null, vararg arguments: Any?) {
        logger.debug { message }
    }

    /**
     * Logs a lazy message at info level.
     */
    fun info(message: () -> String) {
        logger.info { message }
    }

    /**
     * Logs a formatted message at info level.
     */
    fun info(message: String, vararg arguments: Any?) {
        logger.info { message }
    }

    /**
     * Logs an exception at info level.
     */
    fun info(exception: Throwable, message: String? = null, vararg arguments: Any?) {
        logger.info { message }
    }

    /**
     * Logs a lazy message at warn level.
     */
    fun warn(message: () -> String) {
        logger.warn { message }
    }

    /**
     * Logs a formatted message at warn level.
     */
    fun warn(message: String, vararg arguments: Any?) {
        logger.warn { message }
    }

    /**
     * Logs an exception at warn level.
     */
    fun warn(exception: Throwable, message: String? = null, vararg arguments: Any?) {
        logger.warn { message }
    }

    /**
     * Logs a lazy message at error level.
     */
    fun error(message: () -> String) {
        logger.warn { message }
    }

    /**
     * Logs a formatted message at error level.
     */
    fun error(message: String, vararg arguments: Any?) {
        logger.warn { message }
    }

    /**
     * Logs an exception at error level.
     */
    fun error(exception: Throwable, message: String? = null, vararg arguments: Any?) {
        logger.error { message }
    }

    /**
     * Logs an exception at error level (convenience overload).
     */
    fun error(message: String, exception: Throwable) {
        logger.error { message }
    }

    /**
     * Formats a message by replacing {} placeholders with arguments.
     */
    private fun String.formatMessage(vararg arguments: Any?): String {
        var result = this
        arguments.forEach { value ->
            result = result.replaceFirst("{}", value.toString())
        }
        return result
    }
}
