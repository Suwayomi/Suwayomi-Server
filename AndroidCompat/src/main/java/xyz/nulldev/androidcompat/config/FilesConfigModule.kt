package xyz.nulldev.androidcompat.config

import com.typesafe.config.Config
import io.github.config4k.getValue
import xyz.nulldev.ts.config.ConfigModule

/**
 * Files configuration modules. Specifies where to store the Android files.
 */

class FilesConfigModule(getConfig: () -> Config) : ConfigModule(getConfig) {
    val dataDir: String by getConfig()
    val filesDir: String by getConfig()
    val noBackupFilesDir: String by getConfig()
    val externalFilesDirs: MutableList<String> by getConfig()
    val obbDirs: MutableList<String> by getConfig()
    val cacheDir: String by getConfig()
    val codeCacheDir: String by getConfig()
    val externalCacheDirs: MutableList<String> by getConfig()
    val externalMediaDirs: MutableList<String> by getConfig()
    val rootDir: String by getConfig()
    val externalStorageDir: String by getConfig()
    val downloadCacheDir: String by getConfig()
    val databasesDir: String by getConfig()

    val prefsDir: String by getConfig()

    val packageDir: String by getConfig()

    companion object {
        fun register(config: Config) = FilesConfigModule { config.getConfig("android.files") }
    }
}
