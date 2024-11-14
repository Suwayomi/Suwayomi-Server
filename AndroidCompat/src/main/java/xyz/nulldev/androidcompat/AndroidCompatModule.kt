package xyz.nulldev.androidcompat

import android.content.Context
import org.koin.core.module.Module
import org.koin.dsl.module
import xyz.nulldev.androidcompat.androidimpl.CustomContext
import xyz.nulldev.androidcompat.androidimpl.FakePackageManager
import xyz.nulldev.androidcompat.info.ApplicationInfoImpl
import xyz.nulldev.androidcompat.io.AndroidFiles
import xyz.nulldev.androidcompat.pm.PackageController
import xyz.nulldev.androidcompat.service.ServiceSupport

/**
 * AndroidCompatModule
 */

fun androidCompatModule(): Module =
    module {
        single { AndroidFiles() }

        single { ApplicationInfoImpl(get()) }

        single { ServiceSupport() }

        single { FakePackageManager() }

        single { PackageController() }

        single { CustomContext() }

        single<Context> { get<CustomContext>() }
    }
