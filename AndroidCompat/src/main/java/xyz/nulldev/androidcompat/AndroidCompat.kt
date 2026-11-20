package xyz.nulldev.androidcompat

import android.app.Application
import org.koin.mp.KoinPlatformTools
import xyz.nulldev.androidcompat.androidimpl.CustomContext

class AndroidCompat {
    val context: CustomContext by KoinPlatformTools.defaultContext().get().inject()

    fun startApp(application: Application) {
        application.attach(context)
        application.onCreate()
    }
}
