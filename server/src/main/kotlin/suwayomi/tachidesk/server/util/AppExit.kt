package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

enum class ExitCode(
    val code: Int,
) {
    Success(0),
    MutexCheckFailedTachideskRunning(1),
    MutexCheckFailedAnotherAppRunning(2),
    WebUISetupFailure(3),
    ConfigMigrationMisconfiguredFailure(4),
    DbMigrationFailure(5),
}

fun shutdownApp(exitCode: ExitCode) {
    logger.info { "Shutting Down Suwayomi-Server. Goodbye! (reason= ${exitCode.code} (${exitCode.name}))" }

    exitProcess(exitCode.code)
}
