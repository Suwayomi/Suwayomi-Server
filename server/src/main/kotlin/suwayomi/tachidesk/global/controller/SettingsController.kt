package suwayomi.tachidesk.global.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpCode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.global.impl.About
import suwayomi.tachidesk.global.impl.AboutDataClass
import suwayomi.tachidesk.global.impl.AppUpdate
import suwayomi.tachidesk.global.impl.UpdateDataClass
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.PartialServerSettings
import suwayomi.tachidesk.server.ServerConfigDb
import suwayomi.tachidesk.server.ServerSettings
import suwayomi.tachidesk.server.Settings
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.withOperation

/** Settings Page/Screen */
object SettingsController {
    private val json by DI.global.instance<Json>()

    val serverSettings = handler(
        documentWith = {
            withOperation {
                summary("Server settings")
                description("Returns the server settings")
            }
        },
        behaviorOf = { ctx ->
            ctx.json(
                transaction {
                    ServerConfigDb()
                }
            )
        },
        withResults = {
            json<ServerSettings>(HttpCode.OK)
        }
    )

    val modifyServerSettings = handler(
        documentWith = {
            withOperation {
                summary("Server settings")
                description("Modify one or multiple server settings")
            }
            body<PartialServerSettings>()
        },
        behaviorOf = { ctx ->
            val settings = json.decodeFromString<PartialServerSettings>(ctx.body())
            Settings.update(settings)
            ctx.status(HttpCode.OK)
        },
        withResults = {
            httpCode(HttpCode.OK)
        }
    )

    /** returns some static info about the current app build */
    val about = handler(
        documentWith = {
            withOperation {
                summary("About Tachidesk")
                description("Returns some static info about the current app build")
            }
        },
        behaviorOf = { ctx ->
            ctx.json(About.getAbout())
        },
        withResults = {
            json<AboutDataClass>(HttpCode.OK)
        }
    )

    /** check for app updates */
    val checkUpdate = handler(
        documentWith = {
            withOperation {
                summary("Tachidesk update check")
                description("Check for app updates")
            }
        },
        behaviorOf = { ctx ->
            ctx.future(
                future { AppUpdate.checkUpdate() }
            )
        },
        withResults = {
            json<Array<UpdateDataClass>>(HttpCode.OK)
        }
    )
}
