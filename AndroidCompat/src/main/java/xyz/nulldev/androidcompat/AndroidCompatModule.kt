package xyz.nulldev.androidcompat

import android.content.Context
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.conf.global
import org.kodein.di.instance
import org.kodein.di.singleton
import xyz.nulldev.androidcompat.androidimpl.CustomContext
import xyz.nulldev.androidcompat.androidimpl.FakePackageManager
import xyz.nulldev.androidcompat.info.ApplicationInfoImpl
import xyz.nulldev.androidcompat.io.AndroidFiles
import xyz.nulldev.androidcompat.pm.PackageController
import xyz.nulldev.androidcompat.service.ServiceSupport

/**
 * AndroidCompatModule
 */

class AndroidCompatModule {
    fun create() =
        DI.Module("AndroidCompat") {
            bind<AndroidFiles>() with singleton { AndroidFiles() }

            bind<ApplicationInfoImpl>() with singleton { ApplicationInfoImpl() }

            bind<ServiceSupport>() with singleton { ServiceSupport() }

            bind<FakePackageManager>() with singleton { FakePackageManager() }

            bind<PackageController>() with singleton { PackageController() }

            // Context
            bind<CustomContext>() with singleton { CustomContext() }
            bind<Context>() with
                singleton {
                    val context: Context by DI.global.instance<CustomContext>()
                    context
                }
        }
}
