package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.core.security.RouteRole
import io.javalin.http.staticfiles.Location
import io.javalin.plugin.openapi.OpenApiOptions
import io.javalin.plugin.openapi.OpenApiPlugin
import io.javalin.plugin.openapi.ui.SwaggerOptions
import io.swagger.v3.oas.models.info.Info
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.global.GlobalAPI
import suwayomi.tachidesk.graphql.GraphQL
import suwayomi.tachidesk.manga.MangaAPI
import suwayomi.tachidesk.server.util.Browser
import suwayomi.tachidesk.server.util.WebInterfaceManager
import java.io.IOException
import java.lang.IllegalArgumentException
import java.util.concurrent.CompletableFuture
import kotlin.NoSuchElementException
import kotlin.concurrent.thread

object JavalinSetup {
    private val logger = KotlinLogging.logger {}

    private val applicationDirs by DI.global.instance<ApplicationDirs>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun <T> future(block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
        return scope.future(block = block)
    }

    fun javalinSetup() {
        val server = Server()
        val connector =
            ServerConnector(server).apply {
                host = serverConfig.ip.value
                port = serverConfig.port.value
            }
        server.addConnector(connector)

        serverConfig.subscribeTo(combine(serverConfig.ip, serverConfig.port) { ip, port -> Pair(ip, port) }, { (newIp, newPort) ->
            val oldIp = connector.host
            val oldPort = connector.port

            connector.host = newIp
            connector.port = newPort
            connector.stop()
            connector.start()

            logger.info { "Server ip and/or port changed from $oldIp:$oldPort to $newIp:$newPort " }
        })

        val app =
            Javalin.create { config ->
                if (serverConfig.webUIEnabled.value) {
                    val serveWebUI = {
                        config.addSinglePageRoot("/", applicationDirs.webUIRoot + "/index.html", Location.EXTERNAL)
                    }
                    WebInterfaceManager.setServeWebUI(serveWebUI)

                    runBlocking {
                        WebInterfaceManager.setupWebUI()
                    }

                    logger.info { "Serving web static files for ${serverConfig.webUIFlavor.value}" }
                    config.addStaticFiles(applicationDirs.webUIRoot, Location.EXTERNAL)
                    serveWebUI()

                    config.registerPlugin(OpenApiPlugin(getOpenApiOptions()))
                }

                config.server { server }

                config.enableCorsForAllOrigins()

                config.accessManager { handler, ctx, _ ->
                    fun credentialsValid(): Boolean {
                        val (username, password) = ctx.basicAuthCredentials()
                        return username == serverConfig.basicAuthUsername.value && password == serverConfig.basicAuthPassword.value
                    }

                    if (serverConfig.basicAuthEnabled.value && !(ctx.basicAuthCredentialsExist() && credentialsValid())) {
                        ctx.header("WWW-Authenticate", "Basic")
                        ctx.status(401).json("Unauthorized")
                    } else {
                        handler.handle(ctx)
                    }
                }
            }

        app.events { event ->
            event.serverStarted {
                if (serverConfig.initialOpenInBrowserEnabled.value) {
                    Browser.openInBrowser()
                }
            }
        }

        // when JVM is prompted to shutdown, stop javalin gracefully
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                app.stop()
            },
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

        app.exception(IllegalArgumentException::class.java) { e, ctx ->
            logger.error("IllegalArgumentException while handling the request", e)
            ctx.status(400)
            ctx.result(e.message ?: "Bad Request")
        }

        app.routes {
            path("api/") {
                path("v1/") {
                    GlobalAPI.defineEndpoints()
                    MangaAPI.defineEndpoints()
                }
                GraphQL.defineEndpoints()
            }
        }

        app.start()
    }

    private fun getOpenApiOptions(): OpenApiOptions {
        val applicationInfo =
            Info().apply {
                version("1.0")
                description("Suwayomi-Server Api")
            }
        return OpenApiOptions(applicationInfo).apply {
            path("/api/openapi.json")
            swagger(
                SwaggerOptions("/api/swagger-ui").apply {
                    title("Suwayomi-Server Swagger Documentation")
                },
            )
        }
    }

    object Auth {
        enum class Role : RouteRole { ANYONE, USER_READ, USER_WRITE }
    }
}
