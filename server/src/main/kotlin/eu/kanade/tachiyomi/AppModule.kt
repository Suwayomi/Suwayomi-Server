package eu.kanade.tachiyomi

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// import eu.kanade.tachiyomi.data.cache.ChapterCache
// import eu.kanade.tachiyomi.data.cache.CoverCache
// import eu.kanade.tachiyomi.data.database.DatabaseHelper
// import eu.kanade.tachiyomi.data.download.DownloadManager
// import eu.kanade.tachiyomi.data.preference.PreferencesHelper
// import eu.kanade.tachiyomi.data.sync.LibrarySyncManager
// import eu.kanade.tachiyomi.data.track.TrackManager
// import eu.kanade.tachiyomi.extension.ExtensionManager
import android.app.Application
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.serialization.XML
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import rx.Observable
import rx.schedulers.Schedulers
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

//        addSingletonFactory { PreferencesHelper(app) }
//
//        addSingletonFactory { DatabaseHelper(app) }
//
//        addSingletonFactory { ChapterCache(app) }
//
//        addSingletonFactory { CoverCache(app) }

        addSingletonFactory { NetworkHelper(app) }

        addSingletonFactory { JavaScriptEngine(app) }

//        addSingletonFactory { SourceManager(app).also { get<ExtensionManager>().init(it) } }
//
//        addSingletonFactory { ExtensionManager(app) }
//
//        addSingletonFactory { DownloadManager(app) }
//
//        addSingletonFactory { TrackManager(app) }
//
//        addSingletonFactory { LibrarySyncManager(app) }

        addSingletonFactory {
            val json by DI.global.instance<Json>()
            json
        }

        addSingletonFactory {
            val xml by DI.global.instance<XML>()
            xml
        }

        addSingletonFactory {
            val protobuf by DI.global.instance<ProtoBuf>()
            protobuf
        }

        // Asynchronously init expensive components for a faster cold start

//        rxAsync { get<PreferencesHelper>() }

        rxAsync { get<NetworkHelper>() }

        rxAsync {
//            get<SourceManager>()
//            get<DownloadManager>()
        }

//        rxAsync { get<DatabaseHelper>() }
    }

    private fun rxAsync(block: () -> Unit) {
        Observable.fromCallable { block() }.subscribeOn(Schedulers.computation()).subscribe()
    }
}
