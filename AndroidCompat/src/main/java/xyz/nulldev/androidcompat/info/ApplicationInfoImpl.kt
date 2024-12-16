package xyz.nulldev.androidcompat.info

import android.content.pm.ApplicationInfo
import xyz.nulldev.androidcompat.config.ApplicationInfoConfigModule
import xyz.nulldev.ts.config.ConfigManager

class ApplicationInfoImpl(
    private val configManager: ConfigManager,
) : ApplicationInfo() {
    val appInfoConfig: ApplicationInfoConfigModule
        get() = configManager.module()

    val debug: Boolean get() = appInfoConfig.debug

    init {
        super.packageName = appInfoConfig.packageName
    }
}
