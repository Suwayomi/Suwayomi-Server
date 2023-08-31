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

private fun createRollingFileAppender(logContext: LoggerContext, logDirPath: String): RollingFileAppender<ILoggingEvent> {
    val logFilename = "application"

    val logEncoder = PatternLayoutEncoder().apply {
        pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n"
        context = logContext
        start()
    }

    val appender = RollingFileAppender<ILoggingEvent>().apply {
        name = "FILE"
        context = logContext
        encoder = logEncoder
        file = "$logDirPath/$logFilename.log"
    }

    val rollingPolicy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>().apply {
        context = logContext
        setParent(appender)
        fileNamePattern = "$logDirPath/${logFilename}_%d{yyyy-MM-dd}_%i.log.gz"
        setMaxFileSize(FileSize.valueOf("10mb"))
        maxHistory = 14
        setTotalSizeCap(FileSize.valueOf("1gb"))
        start()
    }

    appender.rollingPolicy = rollingPolicy
    appender.start()

    return appender
}

private fun getBaseLogger(): ch.qos.logback.classic.Logger {
    return (KotlinLogging.logger(Logger.ROOT_LOGGER_NAME).underlyingLogger as ch.qos.logback.classic.Logger)
}

fun initLoggerConfig(appRootPath: String) {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val logger = getBaseLogger()
    // logback logs to the console by default (at least when adding a console appender logs in the console are duplicated)
    logger.addAppender(createRollingFileAppender(context, "$appRootPath/logs"))

    // set "kotlin exposed" log level
    context.getLogger("Exposed").level = Level.ERROR

    // gql "ExecutionStrategy" spams logs with "... completing field ..."
    // gql "notprivacysafe" logs every received request multiple times (received, parsing, validating, executing)
    context.getLogger("graphql").level = Level.ERROR
    context.getLogger("notprivacysafe").level = Level.ERROR
}

fun setLogLevel(level: Level) {
    getBaseLogger().level = level
}

fun debugLogsEnabled(config: Config) =
    System.getProperty("suwayomi.tachidesk.config.server.debugLogsEnabled", config.getString("server.debugLogsEnabled")).toBoolean()
