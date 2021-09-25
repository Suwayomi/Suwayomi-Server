package xyz.nulldev.androidcompat.config

import com.typesafe.config.Config
import io.github.config4k.getValue
import xyz.nulldev.ts.config.ConfigModule

/**
 * Application info config.
 */

class ApplicationInfoConfigModule(config: Config) : ConfigModule(config) {
    val packageName: String by config
    val debug: Boolean by config

    companion object {
        fun register(config: Config) =
            ApplicationInfoConfigModule(config.getConfig("android.app"))
    }
}
