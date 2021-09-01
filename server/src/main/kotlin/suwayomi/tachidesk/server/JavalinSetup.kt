package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.http.staticfiles.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.anime.AnimeAPI
import suwayomi.tachidesk.global.GlobalAPI
import suwayomi.tachidesk.manga.MangaAPI
import suwayomi.tachidesk.server.util.Browser
import suwayomi.tachidesk.server.util.setupWebUI
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

object JavalinSetup {
    private val logger = KotlinLogging.logger {}

    private val applicationDirs by DI.global.instance<ApplicationDirs>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun <T> future(block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
        return scope.future(block = block)
    }

    fun javalinSetup() {
        val app = Javalin.create { config ->
            if (serverConfig.webUIEnabled) {
                setupWebUI()

                logger.info { "Serving webUI static files" }
                config.addStaticFiles(applicationDirs.webUIRoot, Location.EXTERNAL)
                config.addSinglePageRoot("/", applicationDirs.webUIRoot + "/index.html", Location.EXTERNAL)
            }

            config.enableCorsForAllOrigins()
        }.events { event ->
            event.serverStarted {
                if (serverConfig.initialOpenInBrowserEnabled) {
                    Browser.openInBrowser()
                }
            }
        }.start(serverConfig.ip, serverConfig.port)

        // when JVM is prompted to shutdown, stop javalin gracefully
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                app.stop()
            }
        )

        app.exception(NullPointerException::class.java) { e, ctx ->
            logger.error("NullPointerException while handling the request", e)
            ctx.status(404)
        }
        app.exception(NoSuchElementException::class.java) { e, ctx ->
            logger.error("NoSuchElementException while handling the request", e)
            ctx.status(404)
        }
        app.exception(IOException::class.java) { e, ctx ->
            logger.error("IOException while handling the request", e)
            ctx.status(500)
            ctx.result(e.message ?: "Internal Server Error")
        }

        app.routes {
            path(serverConfig.baseUrl + "/api/v1/") {
                GlobalAPI.defineEndpoints()
                MangaAPI.defineEndpoints()
                AnimeAPI.defineEndpoints(app) // TODO: migrate Anime endpoints
            }
        }
    }
}
