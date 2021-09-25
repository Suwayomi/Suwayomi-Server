package xyz.nulldev.ts.config

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import mu.KotlinLogging
import org.slf4j.Logger

fun setLogLevel(level: Level) {
    (KotlinLogging.logger(Logger.ROOT_LOGGER_NAME).underlyingLogger as ch.qos.logback.classic.Logger).level = level
}

fun debugLogsEnabled(config: Config) =
    System.getProperty("suwayomi.tachidesk.config.server.debugLogsEnabled", config.getString("server.debugLogsEnabled")).toBoolean()
