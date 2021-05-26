package suwayomi.server.util

import mu.KotlinLogging
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

enum class ExitCode(val code: Int) {
    Success(0),
    MutexCheckFailedTachideskRunning(1),
    MutexCheckFailedAnotherAppRunning(2);
}

fun shutdownApp(exitCode: ExitCode) {
    logger.info("Shutting Down Tachidesk. Goodbye!")

    exitProcess(exitCode.code)
}
