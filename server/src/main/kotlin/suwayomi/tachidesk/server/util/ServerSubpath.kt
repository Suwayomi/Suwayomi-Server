package suwayomi.tachidesk.server.util

import suwayomi.tachidesk.server.serverConfig

object ServerSubpath {
    fun isDefined(): Boolean = raw().isNotBlank()

    private fun raw(): String = serverConfig.webUISubpath.value.trim('/')

    fun normalized(): String = "/${raw()}"

    fun maybeAddAsPrefix(path: String): String {
        if (!isDefined()) {
            return path
        }

        return "${normalized()}/${path.removePrefix("/")}"
    }

    fun maybeAddAsSuffix(path: String): String {
        if (!isDefined()) {
            return path
        }

        return "${path.removeSuffix("/")}/${raw()}/"
    }

    fun asRootPath(): String {
        if (!isDefined()) {
            return "/"
        }

        return "${normalized()}/"
    }
}
