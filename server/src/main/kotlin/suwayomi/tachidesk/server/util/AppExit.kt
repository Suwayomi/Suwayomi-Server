package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import mu.KotlinLogging
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

enum class ExitCode(val code: Int) {
    Success(0),
    MutexCheckFailedTachideskRunning(1),
    MutexCheckFailedAnotherAppRunning(2),
}

fun shutdownApp(exitCode: ExitCode) {
    logger.info("Shutting Down Tachidesk. Goodbye!")

    exitProcess(exitCode.code)
}
