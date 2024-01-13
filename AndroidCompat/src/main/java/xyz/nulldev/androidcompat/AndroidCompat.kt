package xyz.nulldev.androidcompat

import android.app.Application
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import xyz.nulldev.androidcompat.androidimpl.CustomContext

class AndroidCompat {
    val context: CustomContext by DI.global.instance()

    fun startApp(application: Application) {
        application.attach(context)
        application.onCreate()
    }
}
