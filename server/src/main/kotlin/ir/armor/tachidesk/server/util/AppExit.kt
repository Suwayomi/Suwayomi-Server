package ir.armor.tachidesk.server.util

import kotlin.system.exitProcess

enum class ExitCode(val code: Int) {
    Success(0),
    MutexCheckFailedTachideskRunning(1),
    MutexCheckFailedAnotherAppRunning(2);
}

fun shutdownApp(exitCode: ExitCode) {
    exitProcess(exitCode.code)
}
