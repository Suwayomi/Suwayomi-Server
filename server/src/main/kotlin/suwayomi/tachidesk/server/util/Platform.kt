package suwayomi.tachidesk.server.util

data class OSInfo(
    val os: OS,
    val arch: ARCH,
)

object Platform {
    val current: OSInfo by lazy {
        OSInfo(
            OS.from(System.getProperty("os.name")),
            ARCH.from(System.getProperty("os.arch")),
        )
    }
}

sealed class OS(
    val name: String,
    vararg val aliases: String,
) {
    class MACOS(
        name: String,
    ) : OS(name, "mac", "darwin", "osx")

    class LINUX(
        name: String,
    ) : OS(name, "linux")

    class WINDOWS(
        name: String,
    ) : OS(name, "win", "windows")

    val isLinux: Boolean get() = this is LINUX
    val isMacOS: Boolean get() = this is MACOS
    val isWindows: Boolean get() = this is WINDOWS

    private fun matches(value: String): Boolean = aliases.any { value.startsWith(it, ignoreCase = true) }

    companion object {
        private val types =
            listOf(
                ::MACOS,
                ::LINUX,
                ::WINDOWS,
            )

        fun from(name: String): OS =
            types
                .map { it(name) }
                .firstOrNull { it.matches(name) }
                ?: throw UnsupportedOperationException("Unsupported OS: $name")
    }
}

sealed class ARCH(
    val name: String,
    vararg val aliases: String,
) {
    class AMD64(
        name: String,
    ) : ARCH(name, "amd64", "x86_64", "x64")

    class I386(
        name: String,
    ) : ARCH(name, "x86", "i386", "i486", "i586", "i686", "i786")

    class ARM64(
        name: String,
    ) : ARCH(name, "arm64", "aarch64")

    class ARM(
        name: String,
    ) : ARCH(name, "arm")

    private fun matches(value: String): Boolean = aliases.any { value.startsWith(it, ignoreCase = true) }

    companion object {
        private val types =
            listOf(
                ::AMD64,
                ::I386,
                ::ARM64,
                ::ARM,
            )

        fun from(name: String): ARCH =
            types
                .map { it(name) }
                .firstOrNull { it.matches(name) }
                ?: throw UnsupportedOperationException("Unsupported architecture: $name")
    }
}
