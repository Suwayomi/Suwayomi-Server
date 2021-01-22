package eu.kanade.tachiyomi

import android.app.Application
import com.google.gson.Gson
// import eu.kanade.tachiyomi.data.cache.ChapterCache
// import eu.kanade.tachiyomi.data.cache.CoverCache
// import eu.kanade.tachiyomi.data.database.DatabaseHelper
// import eu.kanade.tachiyomi.data.download.DownloadManager
// import eu.kanade.tachiyomi.data.preference.PreferencesHelper
// import eu.kanade.tachiyomi.data.sync.LibrarySyncManager
// import eu.kanade.tachiyomi.data.track.TrackManager
// import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
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

//        addSingletonFactory { SourceManager(app).also { get<ExtensionManager>().init(it) } }
//
//        addSingletonFactory { ExtensionManager(app) }
//
//        addSingletonFactory { DownloadManager(app) }
//
//        addSingletonFactory { TrackManager(app) }
//
//        addSingletonFactory { LibrarySyncManager(app) }

        addSingletonFactory { Gson() }

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
