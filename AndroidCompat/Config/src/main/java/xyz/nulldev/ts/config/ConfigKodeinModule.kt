package xyz.nulldev.ts.config

import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class ConfigKodeinModule {
    fun create() =
        DI.Module("ConfigManager") {
            // Config module
            bind<ConfigManager>() with singleton { GlobalConfigManager }
        }
}
