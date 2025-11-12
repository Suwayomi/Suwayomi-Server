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
 * Logging utility that wraps KotlinLogging for IReader extensions
 */
object Log {
    private val logger = KotlinLogging.logger {}


    /**
     * Logs a lazy message at verbose level. The message will be only evaluated if the log entry is
     * really output.
     *
     * @param message
     * Function that produces the message
     */
    fun verbose(message: () -> String) {
        logger.trace(message)
    }

    /**
     * Logs a formatted message at verbose level. "{}" placeholders will be replaced by given
     * arguments.
     *
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun verbose(
        message: String,
        vararg arguments: Any?,
    ) {
        logger.trace { message.formatMessage(*arguments) }
    }

    /**
     * Logs an exception with a formatted custom message at verbose level. "{}" placeholders will be
     * replaced by given arguments.
     *
     * @param exception
     * Caught exception or any other throwable to log
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun verbose(
        exception: Throwable,
        message: String? = null,
        vararg arguments: Any?,
    ) {
        logger.trace(exception) { message?.formatMessage(*arguments) ?: exception.message ?: "" }
    }

    /**
     * Logs a lazy message at debug level. The message will be only evaluated if the log entry is
     * really output.
     *
     * @param message
     * Function that produces the message
     */
    fun debug(message: () -> String) {
        logger.debug(message)
    }

    /**
     * Logs a formatted message at debug level. "{}" placeholders will be replaced by given
     * arguments.
     *
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun debug(
        message: String,
        vararg arguments: Any?,
    ) {
        logger.debug { message.formatMessage(*arguments) }
    }

    /**
     * Logs an exception with a formatted custom message at debug level. "{}" placeholders will be
     * replaced by given arguments.
     *
     * @param exception
     * Caught exception or any other throwable to log
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun debug(
        exception: Throwable,
        message: String? = null,
        vararg arguments: Any?,
    ) {
        logger.debug(exception) { message?.formatMessage(*arguments) ?: exception.message ?: "" }
    }

    /**
     * Logs a lazy message at info level. The message will be only evaluated if the log entry is
     * really output.
     *
     * @param message
     * Function that produces the message
     */
    fun info(message: () -> String) {
        logger.info(message)
    }

    /**
     * Logs a formatted message at info level. "{}" placeholders will be replaced by given
     * arguments.
     *
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun info(
        message: String,
        vararg arguments: Any?,
    ) {
        logger.info { message.formatMessage(*arguments) }
    }

    /**
     * Logs an exception with a formatted custom message at info level. "{}" placeholders will be
     * replaced by given arguments.
     *
     * @param exception
     * Caught exception or any other throwable to log
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun info(
        exception: Throwable,
        message: String? = null,
        vararg arguments: Any?,
    ) {
        logger.info(exception) { message?.formatMessage(*arguments) ?: exception.message ?: "" }
    }

    /**
     * Logs a lazy message at warn level. The message will be only evaluated if the log entry is
     * really output.
     *
     * @param message
     * Function that produces the message
     */
    fun warn(message: () -> String) {
        logger.warn(message)
    }

    /**
     * Logs a formatted message at warn level. "{}" placeholders will be replaced by given
     * arguments.
     *
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun warn(
        message: String,
        vararg arguments: Any?,
    ) {
        logger.warn { message.formatMessage(*arguments) }
    }

    /**
     * Logs an exception with a formatted custom message at warn level. "{}" placeholders will be
     * replaced by given arguments.
     *
     * @param exception
     * Caught exception or any other throwable to log
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun warn(
        exception: Throwable,
        message: String? = null,
        vararg arguments: Any?,
    ) {
        logger.warn(exception) { message?.formatMessage(*arguments) ?: exception.message ?: "" }
    }

    /**
     * Logs a lazy message at error level. The message will be only evaluated if the log entry is
     * really output.
     *
     * @param message
     * Function that produces the message
     */
    fun error(message: () -> String) {
        logger.error(message)
    }

    /**
     * Logs a formatted message at error level. "{}" placeholders will be replaced by given
     * arguments.
     *
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun error(
        message: String,
        vararg arguments: Any?,
    ) {
        logger.error { message.formatMessage(*arguments) }
    }

    /**
     * Logs an exception with a formatted custom message at error level. "{}" placeholders will be
     * replaced by given arguments.
     *
     * @param exception
     * Caught exception or any other throwable to log
     * @param message
     * Formatted text message to log
     * @param arguments
     * Arguments for formatted text message
     */
    fun error(
        exception: Throwable,
        message: String? = null,
        vararg arguments: Any?,
    ) {
        logger.error(exception) { message?.formatMessage(*arguments) ?: exception.message ?: "" }
    }

    private fun String.formatMessage(vararg arguments: Any?): String {
        var result = this
        arguments.forEach { value ->
            result = result.replaceFirst("{}", value.toString())
        }
        return result
    }
}
