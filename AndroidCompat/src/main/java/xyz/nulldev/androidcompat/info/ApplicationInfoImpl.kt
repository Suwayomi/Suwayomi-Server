package xyz.nulldev.androidcompat.info

import android.content.pm.ApplicationInfo
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.conf.global
import org.kodein.di.instance
import xyz.nulldev.androidcompat.config.ApplicationInfoConfigModule
import xyz.nulldev.ts.config.ConfigManager

class ApplicationInfoImpl(override val di: DI = DI.global) : ApplicationInfo(), DIAware {
    val configManager: ConfigManager by di.instance()

    val appInfoConfig: ApplicationInfoConfigModule
        get() = configManager.module()

    val debug: Boolean get() = appInfoConfig.debug

    init {
        super.packageName = appInfoConfig.packageName
    }
}
