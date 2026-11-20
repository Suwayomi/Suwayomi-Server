package xyz.nulldev.ts.config

import org.koin.core.module.Module
import org.koin.dsl.module

fun configManagerModule(): Module =
    module {
        single<ConfigManager> { GlobalConfigManager }
    }
