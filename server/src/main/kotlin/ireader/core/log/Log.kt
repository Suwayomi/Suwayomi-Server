/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ireader.core.log

import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier

object Log {


  /**
   * Logs a lazy message at verbose level. The message will be only evaluated if the log entry is
   * really output.
   *
   * @param message
   * Function that produces the message
   */
  fun verbose(message: () -> String) {
    Napier.v(message())
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
  fun verbose(message: String, vararg arguments: Any?) {
    Napier.log(priority = LogLevel.VERBOSE, message = message.formatMessage(*arguments))
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
  fun verbose(exception: Throwable, message: String? = null, vararg arguments: Any?) {
    Napier.log(
      priority = LogLevel.VERBOSE,
      message = message?.formatMessage(*arguments) ?: "",
      throwable = exception
    )
  }

  /**
   * Logs a lazy message at debug level. The message will be only evaluated if the log entry is
   * really output.
   *
   * @param message
   * Function that produces the message
   */
  fun debug(message: () -> String) {
    Napier.log(priority = LogLevel.DEBUG, message = message())
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
  fun debug(message: String, vararg arguments: Any?) {
    Napier.log(priority = LogLevel.DEBUG, message = message.formatMessage(*arguments))
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
  fun debug(exception: Throwable, message: String? = null, vararg arguments: Any?) {
    Napier.log(
      priority = LogLevel.DEBUG,
      message = message?.formatMessage(*arguments) ?: "",
      throwable = exception
    )
  }

  /**
   * Logs a lazy message at info level. The message will be only evaluated if the log entry is
   * really output.
   *
   * @param message
   * Function that produces the message
   */
  fun info(message: () -> String) {
    Napier.log(priority = LogLevel.INFO, message = message())
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
  fun info(message: String, vararg arguments: Any?) {
    Napier.log(priority = LogLevel.INFO, message = message.formatMessage(*arguments))
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
  fun info(exception: Throwable, message: String? = null, vararg arguments: Any?) {
    Napier.log(
      priority = LogLevel.INFO,
      message = message?.formatMessage(*arguments) ?: "",
      throwable = exception
    )
  }

  /**
   * Logs a lazy message at warn level. The message will be only evaluated if the log entry is
   * really output.
   *
   * @param message
   * Function that produces the message
   */
  fun warn(message: () -> String) {
    Napier.log(priority = LogLevel.WARNING, message = message())
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
  fun warn(message: String, vararg arguments: Any?) {
    Napier.log(priority = LogLevel.WARNING, message = message.formatMessage(*arguments))
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
  fun warn(exception: Throwable, message: String? = null, vararg arguments: Any?) {
    Napier.log(
      priority = LogLevel.WARNING,
      message = message?.formatMessage(*arguments) ?: "",
      throwable = exception
    )
  }

  /**
   * Logs a lazy message at error level. The message will be only evaluated if the log entry is
   * really output.
   *
   * @param message
   * Function that produces the message
   */
  fun error(message: () -> String) {
    Napier.log(priority = LogLevel.ERROR, message = message())
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
  fun error(message: String, vararg arguments: Any?) {
    Napier.log(priority = LogLevel.ERROR, message = message.formatMessage(*arguments))
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
  fun error(exception: Throwable, message: String? = null, vararg arguments: Any?) {
    Napier.log(priority = LogLevel.ERROR, message = message ?: "", throwable = exception)
  }

  private fun String.formatMessage(vararg arguments: Any?): String {
    var result = this
    arguments.forEach { value ->
      result = result.replaceFirst("{}", value.toString())
    }
    return result
  }
}
