package eu.kanade.tachiyomi

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.registry.default.DefaultRegistrar

open class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Injekt = InjektScope(DefaultRegistrar())
        Injekt.importModule(AppModule(this))

//        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
//        if (BuildConfig.DEBUG) {
//            MultiDex.install(this)
//        }
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//    }
}
