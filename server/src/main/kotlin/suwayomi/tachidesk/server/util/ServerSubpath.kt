package suwayomi.tachidesk.server.util

import suwayomi.tachidesk.server.serverConfig

object ServerSubpath {
    fun isDefined(): Boolean = raw().isNotBlank()

    /**
     * The configured subpath without leading/trailing slashes and without pure `.`/`..`
     * segments — those would escape the prefix during path normalization (a subpath of
     * e.g. `foo/../..` could match requests meant for a different mount point).
     */
    private fun raw(): String =
        serverConfig.webUISubpath.value
            .trim('/')
            .split('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")

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
