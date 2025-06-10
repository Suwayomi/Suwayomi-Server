package xyz.nulldev.androidcompat

import android.util.Log
import android.webkit.WebView
import xyz.nulldev.androidcompat.config.ApplicationInfoConfigModule
import xyz.nulldev.androidcompat.config.FilesConfigModule
import xyz.nulldev.androidcompat.config.SystemConfigModule
import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider
import xyz.nulldev.androidcompat.webkit.PlaywrightWebViewProvider
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

        // Set some properties extensions use
        System.setProperty(
            "http.agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        )
    }

    companion object {
        const val TAG = "AndroidCompatInitializer"

        fun setWebViewImplementation(value: String) {
            when (value.lowercase()) {
                "kcef" -> {
                    Log.i(TAG, "Using KCEF WebView implementation")
                    WebView.setProviderFactory({ view: WebView -> KcefWebViewProvider(view) })
                }
                "playwright" -> {
                    Log.i(TAG, "Using Playwright WebView implementation")
                    WebView.setProviderFactory({ view: WebView -> PlaywrightWebViewProvider(view) })
                }
                else -> {
                    Log.e(TAG, "Invalid setting $value for server.webviewImpl, no WebView implementation set")
                }
            }
        }
    }
}
