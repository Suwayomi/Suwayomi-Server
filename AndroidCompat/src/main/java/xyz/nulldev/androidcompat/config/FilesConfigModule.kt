package xyz.nulldev.androidcompat.config

import com.typesafe.config.Config
import xyz.nulldev.ts.config.ConfigModule

/**
 * Files configuration modules. Specifies where to store the Android files.
 */

class FilesConfigModule(config: Config) : ConfigModule(config) {
    val dataDir = config.getString("dataDir")!!
    val filesDir = config.getString("filesDir")!!
    val noBackupFilesDir = config.getString("noBackupFilesDir")!!
    val externalFilesDirs: MutableList<String> = config.getStringList("externalFilesDirs")!!
    val obbDirs: MutableList<String> = config.getStringList("obbDirs")!!
    val cacheDir = config.getString("cacheDir")!!
    val codeCacheDir = config.getString("codeCacheDir")!!
    val externalCacheDirs: MutableList<String> = config.getStringList("externalCacheDirs")!!
    val externalMediaDirs: MutableList<String> = config.getStringList("externalMediaDirs")!!
    val rootDir = config.getString("rootDir")!!
    val externalStorageDir = config.getString("externalStorageDir")!!
    val downloadCacheDir = config.getString("downloadCacheDir")!!
    val databasesDir = config.getString("databasesDir")!!

    val prefsDir = config.getString("prefsDir")!!

    val packageDir = config.getString("packageDir")!!

    companion object {
        fun register(config: Config)
                = FilesConfigModule(config.getConfig("android.files"))
    }
}