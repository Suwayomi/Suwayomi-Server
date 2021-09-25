package xyz.nulldev.androidcompat.config

import com.typesafe.config.Config
import io.github.config4k.getValue
import xyz.nulldev.ts.config.ConfigModule

/**
 * Files configuration modules. Specifies where to store the Android files.
 */

class FilesConfigModule(config: Config) : ConfigModule(config) {
    val dataDir: String by config
    val filesDir: String by config
    val noBackupFilesDir: String by config
    val externalFilesDirs: MutableList<String> by config
    val obbDirs: MutableList<String> by config
    val cacheDir: String by config
    val codeCacheDir: String by config
    val externalCacheDirs: MutableList<String> by config
    val externalMediaDirs: MutableList<String> by config
    val rootDir: String by config
    val externalStorageDir: String by config
    val downloadCacheDir: String by config
    val databasesDir: String by config

    val prefsDir: String by config

    val packageDir: String by config

    companion object {
        fun register(config: Config) =
            FilesConfigModule(config.getConfig("android.files"))
    }
}
