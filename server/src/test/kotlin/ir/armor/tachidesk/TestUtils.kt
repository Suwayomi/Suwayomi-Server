package ir.armor.tachidesk

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Level
import mu.KotlinLogging
import org.slf4j.Logger

fun setLoggingEnabled(enabled: Boolean = true) {
    val logger = (KotlinLogging.logger(Logger.ROOT_LOGGER_NAME).underlyingLogger as ch.qos.logback.classic.Logger)
    logger.level = if (enabled) {
        Level.DEBUG
    } else Level.ERROR
}
