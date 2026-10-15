package xyz.nulldev.androidcompat

import android.app.Application
import org.koin.mp.KoinPlatformTools
import xyz.nulldev.androidcompat.androidimpl.CustomContext

/**
 * Entry point of the Android compatibility layer.
 *
 * NOTE: [context] is a process-wide singleton shared by every extension
 * (single-tenant model). Per-extension state must therefore never be stored
 * on the Context; use the package-scoped APIs (e.g. PackageController) to
 * distinguish extensions.
 */
class AndroidCompat {
    val context: CustomContext by KoinPlatformTools.defaultContext().get().inject()

    fun startApp(application: Application) {
        application.attach(context)
        application.onCreate()
    }
}
