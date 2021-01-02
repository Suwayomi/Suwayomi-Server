package xyz.nulldev.androidcompat

import org.kodein.di.DI
import org.kodein.di.conf.global
import xyz.nulldev.androidcompat.bytecode.ModApplier
import xyz.nulldev.androidcompat.config.ApplicationInfoConfigModule
import xyz.nulldev.androidcompat.config.FilesConfigModule
import xyz.nulldev.androidcompat.config.SystemConfigModule
import xyz.nulldev.ts.config.GlobalConfigManager

/**
 * Initializes the Android compatibility module
 */
class AndroidCompatInitializer {

    val modApplier by lazy { ModApplier() }

    fun init() {
        modApplier.apply()

        DI.global.addImport(AndroidCompatModule().create())

        //Register config modules
        GlobalConfigManager.registerModules(
            FilesConfigModule.register(GlobalConfigManager.config),
            ApplicationInfoConfigModule.register(GlobalConfigManager.config),
            SystemConfigModule.register(GlobalConfigManager.config)
        )
    }
}
