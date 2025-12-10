package ireader.core.storage

import java.io.File

val AppDir : File = getCacheDir()

val ExtensionDir = File(AppDir, "/Extensions/")
val BackupDir = File(AppDir, "/Backup/")

enum class OperatingSystem {
    Android, IOS, Windows, Linux, MacOS, Unknown
}

private val currentOperatingSystem: OperatingSystem
    get() {
        val operSys = System.getProperty("os.name").lowercase()
        return if (operSys.contains("win")) {
            OperatingSystem.Windows
        } else if (operSys.contains("nix") || operSys.contains("nux") ||
                operSys.contains("aix")
        ) {
            OperatingSystem.Linux
        } else if (operSys.contains("mac")) {
            OperatingSystem.MacOS
        } else {
            OperatingSystem.Unknown
        }
    }

private fun getCacheDir(): File  {
    val ApplicationName = "IReader"
    return when (currentOperatingSystem) {
        OperatingSystem.Windows -> File(System.getenv("AppData"), "$ApplicationName/cache/")
        OperatingSystem.Linux -> File(System.getProperty("user.home"), ".cache/$ApplicationName/")
        OperatingSystem.MacOS -> File(System.getProperty("user.home"), "Library/Caches/$ApplicationName/")
        else -> throw IllegalStateException("Unsupported operating system")
    }
}

