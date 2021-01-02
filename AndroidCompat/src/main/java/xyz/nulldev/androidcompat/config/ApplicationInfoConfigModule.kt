package xyz.nulldev.androidcompat.config

import com.typesafe.config.Config
import xyz.nulldev.ts.config.ConfigModule

/**
 * Application info config.
 */

class ApplicationInfoConfigModule(config: Config) : ConfigModule(config) {
    val packageName = config.getString("packageName")!!
    val debug = config.getBoolean("debug")

    companion object {
        fun register(config: Config)
                = ApplicationInfoConfigModule(config.getConfig("android.app"))
    }
}
