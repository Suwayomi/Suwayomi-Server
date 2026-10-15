package xyz.nulldev.androidcompat

import android.webkit.WebView
import xyz.nulldev.androidcompat.config.ApplicationInfoConfigModule
import xyz.nulldev.androidcompat.config.FilesConfigModule
import xyz.nulldev.androidcompat.config.SystemConfigModule
import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider
import xyz.nulldev.ts.config.GlobalConfigManager

/**
 * Initializes the Android compatibility module
 */
class AndroidCompatInitializer {
    fun init() {
        // Register config modules
        GlobalConfigManager.registerModules(
            FilesConfigModule.register(GlobalConfigManager.config),
            ApplicationInfoConfigModule.register(GlobalConfigManager.config),
            SystemConfigModule.register(GlobalConfigManager.config),
        )

        WebView.setProviderFactory({ view: WebView -> KcefWebViewProvider(view) })

        // NOTE: we deliberately do NOT set the JVM-global "http.agent" system
        // property here. The canonical default User-Agent lives in
        // KcefWebSettings.DEFAULT_USER_AGENT and is consumed by the WebView
        // provider; mutating a global system property would leak into
        // unrelated HttpClients in this process.
    }
}
