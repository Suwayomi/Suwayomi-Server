package suwayomi.tachidesk.server.util

import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.graphql.types.JvmInfo
import suwayomi.tachidesk.graphql.types.OSInfo
import java.awt.GraphicsEnvironment
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

private val logger = KotlinLogging.logger { }

data class PlatformInfo(
    val os: OS,
    val arch: ARCH,
    val headless: Boolean,
    val jvm: JvmInfo,
)

object Platform {
    val current: PlatformInfo by lazy {
        PlatformInfo(
            os = OS.from(System.getProperty("os.name")),
            arch = ARCH.from(System.getProperty("os.arch")),
            headless = GraphicsEnvironment.isHeadless(),
            jvm = getJvmInfo(),
        )
    }
}

private fun getJvmInfo(): JvmInfo =
    JvmInfo(
        javaVersion = System.getProperty("java.version"),
        vmName = System.getProperty("java.vm.name"),
        vmVersion = System.getProperty("java.vm.version"),
        vmVendor = System.getProperty("java.vm.vendor"),
    )

sealed class OS(
    val name: String,
    vararg val aliases: String,
) {
    val version: String by lazy { System.getProperty("os.version") }

    class MACOS(
        name: String,
    ) : OS(name, "mac", "darwin", "osx") {
        override val details: OSInfo by lazy(::loadDetails)

        override fun loadDetails(): OSInfo {
            val productName = runCommand("sw_vers", "-productName").firstOrNull()
            val productVersion = runCommand("sw_vers", "-productVersion").firstOrNull()
            val buildVersion = runCommand("sw_vers", "-buildVersion").firstOrNull()

            return OSInfo(
                name = productName ?: name,
                version = productVersion ?: version,
                build = buildVersion,
            )
        }
    }

    class LINUX(
        name: String,
    ) : OS(name, "linux") {
        override val details: OSInfo by lazy(::loadDetails)

        override fun loadDetails(): OSInfo {
            val osRelease = readOsRelease()

            return OSInfo(
                name = osRelease["PRETTY_NAME"] ?: osRelease["NAME"] ?: name,
                version = osRelease["VERSION_ID"] ?: version,
                build = version,
            )
        }

        private fun readOsRelease(): Map<String, String> {
            val path = Path("/etc/os-release")

            if (!path.isRegularFile()) {
                return emptyMap()
            }

            return path
                .readLines()
                .mapNotNull { line ->
                    val trimmed = line.trim()

                    if (trimmed.isEmpty() || trimmed.startsWith('#')) {
                        return@mapNotNull null
                    }

                    val (key, value) = trimmed.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null

                    key to value.trim('"')
                }.toMap()
        }
    }

    class WINDOWS(
        name: String,
    ) : OS(name, "win", "windows") {
        override val details: OSInfo by lazy(::loadDetails)

        override fun loadDetails(): OSInfo {
            val productName = registryValue("ProductName") ?: name

            val currentBuildNumber = registryValue("CurrentBuildNumber")
            val currentBuild = registryValue("CurrentBuild")
            val build = currentBuildNumber ?: currentBuild

            val tmpVersion = registryValue("DisplayVersion") ?: registryValue("ReleaseId") ?: version
            val displayVersion =
                if (tmpVersion != version) {
                    "$version.$build ($tmpVersion)"
                } else {
                    tmpVersion
                }

            val displayName =
                if ((build?.toIntOrNull() ?: 0) >= 22000) {
                    productName.replace("Windows 10", "Windows 11")
                } else {
                    productName
                }

            return OSInfo(
                name = displayName,
                version = displayVersion,
                build = build,
            )
        }

        private fun registryValue(name: String): String? {
            val output =
                runCommand(
                    "reg",
                    "query",
                    """HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion""",
                    "/v",
                    name,
                )

            return output
                .firstOrNull { it.contains(name) }
                ?.substringAfter("REG_SZ")
                ?.trim()
        }
    }

    /**
     * Catch-all for operating systems we have no specifics for (FreeBSD, Solaris, ...). Keeps
     * [Platform.current] - and therefore every setting that consults the OS - usable on
     * any JVM instead of crashing during `ServerConfig` construction.
     */
    class UNKNOWN(
        name: String,
    ) : OS(name) {
        override val details: OSInfo by lazy(::loadDetails)

        override fun loadDetails(): OSInfo =
            OSInfo(
                name = name,
                version = version,
            )
    }

    val isLinux: Boolean get() = this is LINUX
    val isMacOS: Boolean get() = this is MACOS
    val isWindows: Boolean get() = this is WINDOWS

    abstract val details: OSInfo

    protected abstract fun loadDetails(): OSInfo

    private fun matches(value: String): Boolean = aliases.any { value.startsWith(it, ignoreCase = true) }

    protected fun runCommand(vararg command: String): List<String> {
        val process =
            try {
                ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()
            } catch (e: Exception) {
                logger.debug(e) { "Could not run command ${command.joinToString(" ")}" }
                return emptyList()
            }

        // drain stdout asynchronously so a noisy child can't deadlock on a full pipe buffer
        val output =
            CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().readLines()
            }

        val exited = process.waitFor(RUN_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            return emptyList()
        }

        if (process.exitValue() != 0) {
            return emptyList()
        }

        return runCatching { output.get(5, TimeUnit.SECONDS) }.getOrElse { emptyList() }
    }

    companion object {
        private const val RUN_COMMAND_TIMEOUT_SECONDS = 10L

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
                ?: UNKNOWN(name).also {
                    logger.warn { "Unsupported OS '$name', treating it as unknown" }
                }
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

    class RISCV(
        name: String,
    ) : ARCH(name, "riscv", "riscv32", "riscv64")

    /**
     * Catch-all for architectures we have no specifics for; see [OS.UNKNOWN].
     */
    class UNKNOWN(
        name: String,
    ) : ARCH(name)

    private fun matches(value: String): Boolean = aliases.any { value.startsWith(it, ignoreCase = true) }

    companion object {
        private val types =
            listOf(
                ::AMD64,
                ::I386,
                ::ARM64,
                ::ARM,
                ::RISCV,
            )

        fun from(name: String): ARCH =
            types
                .map { it(name) }
                .firstOrNull { it.matches(name) }
                ?: UNKNOWN(name).also {
                    logger.warn { "Unsupported architecture '$name', treating it as unknown" }
                }
    }
}
