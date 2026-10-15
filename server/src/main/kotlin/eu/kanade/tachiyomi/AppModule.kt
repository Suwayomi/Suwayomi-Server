package eu.kanade.tachiyomi

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

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

// TODO: Injekt -> Koin migration is still in progress; several tachiyomi-core
//  components (e.g. HttpSource.network) are still obtained via Injekt.
fun createAppModule(app: Application): Module =
    module {
        single { app }

        single { NetworkHelper(app) }

        single { JavaScriptEngine(app) }

        single<Json> {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }

        single<XML> {
            XML.v1 {
                policy {
                    ignoreUnknownChildren()
                    autoPolymorphic = true
                }
                xmlDeclMode = XmlDeclMode.Charset
                xmlVersion = XmlVersion.XML10
                setIndent(2)
            }
        }

        single<ProtoBuf> {
            ProtoBuf
        }
    }
