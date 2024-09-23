package xyz.nulldev.ts.config

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import com.typesafe.config.Config
import mu.KotlinLogging
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private fun fileSizeValueOfOrDefault(
    fileSizeStr: String,
    default: String,
): FileSize =
    try {
        FileSize.valueOf(fileSizeStr)
    } catch (e: IllegalArgumentException) {
        FileSize.valueOf(default)
    }

private const val FILE_APPENDER_NAME = "SuwayomiDefaultAppender"

private fun createRollingFileAppender(
    logContext: LoggerContext,
    logDirPath: String,
    maxFiles: Int,
    maxFileSize: String,
    maxTotalSize: String,
): RollingFileAppender<ILoggingEvent> {
    val logFilename = "application"

    val logEncoder =
        PatternLayoutEncoder().apply {
            pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n"
            context = logContext
            start()
        }

    val appender =
        RollingFileAppender<ILoggingEvent>().apply {
            name = FILE_APPENDER_NAME
            context = logContext
            encoder = logEncoder
            file = "$logDirPath/$logFilename.log"
        }

    val rollingPolicy =
        SizeAndTimeBasedRollingPolicy<ILoggingEvent>().apply {
            context = logContext
            setParent(appender)
            fileNamePattern = "$logDirPath/${logFilename}_%d{yyyy-MM-dd}_%i.log.gz"
            maxHistory = maxFiles.coerceAtLeast(0)
            setMaxFileSize(fileSizeValueOfOrDefault(maxFileSize, "10mb"))
            setTotalSizeCap(fileSizeValueOfOrDefault(maxTotalSize, "100mb"))
            start()
        }

    appender.rollingPolicy = rollingPolicy
    appender.start()

    return appender
}

private fun getBaseLogger(): ch.qos.logback.classic.Logger =
    (KotlinLogging.logger(Logger.ROOT_LOGGER_NAME).underlyingLogger as ch.qos.logback.classic.Logger)

private fun getLogger(name: String): ch.qos.logback.classic.Logger {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    return context.getLogger(name)
}

fun initLoggerConfig(
    appRootPath: String,
    maxFiles: Int,
    maxFileSize: String,
    maxTotalSize: String,
) {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val logger = getBaseLogger()

    // logback logs to the console by default (at least when adding a console appender logs in the console are duplicated)
    logger.addAppender(createRollingFileAppender(context, "$appRootPath/logs", maxFiles, maxFileSize, maxTotalSize))

    // set "kotlin exposed" log level
    setLogLevelFor("Exposed", Level.ERROR)
}

fun updateFileAppender(
    maxFiles: Int,
    maxFileSize: String,
    maxTotalSize: String,
) {
    val logger = getBaseLogger()

    val appender = logger.getAppender(FILE_APPENDER_NAME) as RollingFileAppender<*>? ?: return
    val rollingPolicy = appender.rollingPolicy as SizeAndTimeBasedRollingPolicy<*>
    rollingPolicy.apply {
        maxHistory = maxFiles
        setMaxFileSize(FileSize.valueOf(maxFileSize))
        setTotalSizeCap(FileSize.valueOf(maxTotalSize))

        rollingPolicy.stop()
        appender.stop()

        rollingPolicy.start()
        appender.start()
    }
}

const val BASE_LOGGER_NAME = "_BaseLogger"

fun setLogLevelFor(
    name: String,
    level: Level,
) {
    val logger =
        if (name == BASE_LOGGER_NAME) {
            getBaseLogger()
        } else {
            getLogger(name)
        }

    logger.level = level
}

fun debugLogsEnabled(config: Config) =
    System.getProperty("suwayomi.tachidesk.config.server.debugLogsEnabled", config.getString("server.debugLogsEnabled")).toBoolean()
