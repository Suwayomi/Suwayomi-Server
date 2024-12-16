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
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.core.module.Module
import org.koin.dsl.module

fun createAppModule(app: Application): Module {
    return module {
        single { app }

//        addSingletonFactory { PreferencesHelper(app) }
//
//        addSingletonFactory { DatabaseHelper(app) }
//
//        addSingletonFactory { ChapterCache(app) }
//
//        addSingletonFactory { CoverCache(app) }

        single { NetworkHelper(app) }

        single { JavaScriptEngine(app) }

//        addSingletonFactory { SourceManager(app).also { get<ExtensionManager>().init(it) } }
//
//        addSingletonFactory { ExtensionManager(app) }
//
//        addSingletonFactory { DownloadManager(app) }
//
//        addSingletonFactory { TrackManager(app) }
//
//        addSingletonFactory { LibrarySyncManager(app) }

        single {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }

        single {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = XmlDeclMode.Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }

        single {
            ProtoBuf
        }
    }

    // Asynchronously init expensive components for a faster cold start

//        rxAsync { get<PreferencesHelper>() }

//        rxAsync {
//            get<SourceManager>()
//            get<DownloadManager>()
//        }

//        rxAsync { get<DatabaseHelper>() }
}
