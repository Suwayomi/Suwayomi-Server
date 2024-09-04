package xyz.nulldev.androidcompat.io

import xyz.nulldev.androidcompat.config.FilesConfigModule
import xyz.nulldev.ts.config.ConfigManager
import xyz.nulldev.ts.config.GlobalConfigManager
import java.io.File

/**
 * Android file constants.
 */
class AndroidFiles(
    val configManager: ConfigManager = GlobalConfigManager,
) {
    val filesConfig: FilesConfigModule
        get() = configManager.module()

    val dataDir: File get() = registerFile(filesConfig.dataDir)
    val filesDir: File get() = registerFile(filesConfig.filesDir)
    val noBackupFilesDir: File get() = registerFile(filesConfig.noBackupFilesDir)
    val externalFilesDirs: List<File> get() = filesConfig.externalFilesDirs.map { registerFile(it) }
    val obbDirs: List<File> get() = filesConfig.obbDirs.map { registerFile(it) }
    val cacheDir: File get() = registerFile(filesConfig.cacheDir)
    val codeCacheDir: File get() = registerFile(filesConfig.codeCacheDir)
    val externalCacheDirs: List<File> get() = filesConfig.externalCacheDirs.map { registerFile(it) }
    val externalMediaDirs: List<File> get() = filesConfig.externalMediaDirs.map { registerFile(it) }
    val rootDir: File get() = registerFile(filesConfig.rootDir)
    val externalStorageDir: File get() = registerFile(filesConfig.externalStorageDir)
    val downloadCacheDir: File get() = registerFile(filesConfig.downloadCacheDir)
    val databasesDir: File get() = registerFile(filesConfig.databasesDir)

    val prefsDir: File get() = registerFile(filesConfig.prefsDir)

    val packagesDir: File get() = registerFile(filesConfig.packageDir)

    fun registerFile(file: String): File =
        File(file).apply {
            mkdirs()
        }
}
