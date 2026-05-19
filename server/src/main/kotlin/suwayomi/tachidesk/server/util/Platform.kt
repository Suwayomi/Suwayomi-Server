package suwayomi.tachidesk.server.util

import java.util.Locale

data class OSInfo(
    val os: OS,
    val arch: ARCH,
)

object Platform {
    private val oses: List<OS.OSCreator> = listOf(OS.OSCreator.MACOSX(), OS.OSCreator.LINUX(), OS.OSCreator.WINDOWS())
    private val archs: List<ARCH.ARCHCreator> =
        listOf(ARCH.ARCHCreator.AMD64(), ARCH.ARCHCreator.I386(), ARCH.ARCHCreator.ARM64(), ARCH.ARCHCreator.ARM())

    val current: OSInfo by lazy { getCurrentPlatform() }

    private fun getCurrentPlatform(): OSInfo {
        val osName = System.getProperty("os.name")
        val archName = System.getProperty("os.arch")
        val os = oses.firstNotNullOfOrNull { if (it.matches(osName)) it.create(osName) else null }
        val arch = archs.firstNotNullOfOrNull { if (it.matches(archName)) it.create(archName) else null }
        if (os == null || arch == null) {
            throw UnsupportedOperationException("Unsupported platform tuple $osName,$archName")
        }
        return OSInfo(os, arch)
    }
}

sealed class OS(
    val name: String,
    vararg val values: String,
) {
    internal abstract class OSCreator(
        vararg val values: String,
    ) {
        abstract fun create(name: String): OS

        fun matches(name: String): Boolean =
            values.any { name.startsWith(it, true) } ||
                values.contains(
                    name.lowercase(
                        Locale.ENGLISH,
                    ),
                )

        class MACOSX : OSCreator("mac", "darwin", "osx") {
            override fun create(name: String) = OS.MACOSX(name, *values)
        }

        class LINUX : OSCreator("linux") {
            override fun create(name: String) = OS.LINUX(name, *values)
        }

        class WINDOWS : OSCreator("win", "windows") {
            override fun create(name: String) = OS.WINDOWS(name, *values)
        }
    }

    class MACOSX(
        name: String,
        vararg values: String,
    ) : OS(name, *values)

    class LINUX(
        name: String,
        vararg values: String,
    ) : OS(name, *values)

    class WINDOWS(
        name: String,
        vararg values: String,
    ) : OS(name, *values)

    val isLinux: Boolean get() = this is LINUX
    val isMacOSX: Boolean get() = this is MACOSX
    val isWindows: Boolean get() = this is WINDOWS
}

sealed class ARCH(
    val name: String,
    vararg val values: String,
) {
    internal abstract class ARCHCreator(
        vararg val values: String,
    ) {
        abstract fun create(name: String): ARCH

        fun matches(name: String): Boolean =
            values.any { name.startsWith(it, true) } ||
                values.contains(
                    name.lowercase(
                        Locale.ENGLISH,
                    ),
                )

        class AMD64 : ARCHCreator("amd64", "x86_64", "x64") {
            override fun create(name: String) = ARCH.AMD64(name, *values)
        }

        class I386 : ARCHCreator("x86", "i386", "i486", "i586", "i686", "i786") {
            override fun create(name: String) = ARCH.I386(name, *values)
        }

        class ARM64 : ARCHCreator("arm64", "aarch64") {
            override fun create(name: String) = ARCH.ARM64(name, *values)
        }

        class ARM : ARCHCreator("arm") {
            override fun create(name: String) = ARCH.ARM(name, *values)
        }
    }

    class AMD64(
        arch: String,
        vararg values: String,
    ) : ARCH(arch, *values)

    class I386(
        arch: String,
        vararg values: String,
    ) : ARCH(arch, *values)

    class ARM64(
        arch: String,
        vararg values: String,
    ) : ARCH(arch, *values)

    class ARM(
        arch: String,
        vararg values: String,
    ) : ARCH(arch, *values)
}
