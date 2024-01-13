package xyz.nulldev.androidcompat.config

import com.typesafe.config.Config
import io.github.config4k.getValue
import xyz.nulldev.ts.config.ConfigModule

/**
 * Application info config.
 */

class ApplicationInfoConfigModule(getConfig: () -> Config) : ConfigModule(getConfig) {
    val packageName: String by getConfig()
    val debug: Boolean by getConfig()

    companion object {
        fun register(config: Config) = ApplicationInfoConfigModule { config.getConfig("android.app") }
    }
}
