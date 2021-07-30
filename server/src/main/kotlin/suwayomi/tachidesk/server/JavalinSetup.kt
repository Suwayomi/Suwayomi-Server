package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.Javalin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import mu.KotlinLogging
import suwayomi.tachidesk.anime.AnimeAPI
import suwayomi.tachidesk.manga.TachideskAPI
import suwayomi.tachidesk.server.util.Browser
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

object JavalinSetup {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun <T> future(block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
        return scope.future(block = block)
    }

    fun javalinSetup() {
        var hasWebUiBundled = false

        val app = Javalin.create { config ->
            try {
                // if the bellow line throws an exception then webUI is not bundled
                this::class.java.getResource("/webUI/index.html")

                // no exception so we can tell javalin to serve webUI
                hasWebUiBundled = true
                config.addStaticFiles("/webUI")
                config.addSinglePageRoot("/", "/webUI/index.html")
            } catch (e: RuntimeException) {
                logger.warn("react build files are missing.")
                hasWebUiBundled = false
            }
            config.enableCorsForAllOrigins()
        }.events { event ->
            event.serverStarted {
                if (hasWebUiBundled && serverConfig.initialOpenInBrowserEnabled) {
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

        TachideskAPI.defineEndpoints(app)
        AnimeAPI.defineEndpoints(app)
    }
}
