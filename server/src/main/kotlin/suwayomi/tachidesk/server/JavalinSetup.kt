package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import io.javalin.http.RedirectResponse
import io.javalin.http.UnauthorizedResponse
import io.javalin.http.staticfiles.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import org.eclipse.jetty.server.ServerConnector
import suwayomi.tachidesk.global.GlobalAPI
import suwayomi.tachidesk.graphql.GraphQL
import suwayomi.tachidesk.manga.MangaAPI
import suwayomi.tachidesk.opds.OpdsAPI
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.util.Browser
import suwayomi.tachidesk.server.util.WebInterfaceManager
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.days

object JavalinSetup {
    private val logger = KotlinLogging.logger {}

    private val applicationDirs: ApplicationDirs by injectLazy()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun <T> future(block: suspend CoroutineScope.() -> T): CompletableFuture<T> = scope.future(block = block)

    fun javalinSetup() {
        val app =
            Javalin.create { config ->
                if (serverConfig.webUIEnabled.value) {
                    val serveWebUI = {
                        config.spaRoot.addFile("/", applicationDirs.webUIRoot + "/index.html", Location.EXTERNAL)
                    }
                    WebInterfaceManager.setServeWebUI(serveWebUI)

                    runBlocking {
                        WebInterfaceManager.setupWebUI()
                    }

                    logger.info { "Serving web static files for ${serverConfig.webUIFlavor.value}" }
                    config.staticFiles.add(applicationDirs.webUIRoot, Location.EXTERNAL)
                    serveWebUI()

                    // config.registerPlugin(OpenApiPlugin(getOpenApiOptions()))
                }

                var connectorAdded = false
                config.jetty.modifyServer { server ->
                    if (!connectorAdded) {
                        val connector =
                            ServerConnector(server).apply {
                                host = serverConfig.ip.value
                                port = serverConfig.port.value
                            }
                        server.addConnector(connector)

                        serverConfig.subscribeTo(
                            combine(
                                serverConfig.ip,
                                serverConfig.port,
                            ) { ip, port -> Pair(ip, port) },
                            { (newIp, newPort) ->
                                val oldIp = connector.host
                                val oldPort = connector.port

                                connector.host = newIp
                                connector.port = newPort
                                connector.stop()
                                connector.start()

                                logger.info { "Server ip and/or port changed from $oldIp:$oldPort to $newIp:$newPort " }
                            },
                        )
                        connectorAdded = true
                    }
                }

                config.bundledPlugins.enableCors { cors ->
                    cors.addRule {
                        it.allowCredentials = true
                        it.reflectClientOrigin = true
                    }
                }

                config.router.apiBuilder {
                    path("api/") {
                        path("v1/") {
                            GlobalAPI.defineEndpoints()
                            MangaAPI.defineEndpoints()
                        }

                        OpdsAPI.defineEndpoints()
                        GraphQL.defineEndpoints()
                    }
                }
            }

        app.get("/login.html") { ctx ->
            var page =
                this::class.java
                    .getResourceAsStream("/static/login.html")!!
                    .use { it.readAllBytes() }
                    .toString(Charsets.UTF_8)
            page = page.replace("[VERSION]", BuildConfig.VERSION).replace("[ERROR]", "")
            ctx.header("content-type", "text/html")
            val httpCacheSeconds = 1.days.inWholeSeconds
            ctx.header("cache-control", "max-age=$httpCacheSeconds")
            ctx.result(page)
        }

        app.post("/login.html") { ctx ->
            val username = ctx.formParam("user")
            val password = ctx.formParam("pass")
            val isValid =
                username == serverConfig.basicAuthUsername.value &&
                    password == serverConfig.basicAuthPassword.value

            if (isValid) {
                val redirect = ctx.queryParam("redirect") ?: "/"
                // NOTE: We currently have no session handler attached.
                // Thus, all sessions are stored in memory and not persisted.
                // Furthermore, default session timeout appears to be 30m
                ctx.header("Location", redirect)
                ctx.sessionAttribute("logged-in", username)
                throw RedirectResponse(HttpStatus.SEE_OTHER)
            }

            var page =
                this::class.java
                    .getResourceAsStream("/static/login.html")!!
                    .use { it.readAllBytes() }
                    .toString(Charsets.UTF_8)
            page = page.replace("[VERSION]", BuildConfig.VERSION).replace("[ERROR]", "Invalid username or password")
            ctx.header("content-type", "text/html")
            ctx.req().session.invalidate()
            ctx.result(page)
        }

        app.beforeMatched { ctx ->
            val isWebManifest = listOf("site.webmanifest", "manifest.json", "login.html").any { ctx.path().endsWith(it) }
            val isPreFlight = ctx.method() == HandlerType.OPTIONS

            val requiresAuthentication = !isPreFlight && !isWebManifest
            if (!requiresAuthentication) {
                return@beforeMatched
            }

            fun credentialsValid(): Boolean {
                val basicAuthCredentials = ctx.basicAuthCredentials() ?: return false
                val (username, password) = basicAuthCredentials
                return username == serverConfig.basicAuthUsername.value &&
                    password == serverConfig.basicAuthPassword.value
            }

            fun cookieValid(): Boolean {
                val username = ctx.sessionAttribute<String>("logged-in") ?: return false
                return username == serverConfig.basicAuthUsername.value
            }

            if (serverConfig.cookieAuthEnabled.value && !cookieValid() && ctx.path().startsWith("/api")) {
                throw UnauthorizedResponse()
            }

            if (serverConfig.cookieAuthEnabled.value && !cookieValid()) {
                val url = "/login.html?redirect=" + URLEncoder.encode(ctx.fullUrl(), StandardCharsets.UTF_8)
                ctx.header("Location", url)
                throw RedirectResponse(HttpStatus.SEE_OTHER)
            }

            if (serverConfig.basicAuthEnabled.value && !credentialsValid()) {
                ctx.header("WWW-Authenticate", "Basic")
                throw UnauthorizedResponse()
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
            logger.error(e) { "NullPointerException while handling the request" }
            ctx.status(404)
        }
        app.exception(NoSuchElementException::class.java) { e, ctx ->
            logger.error(e) { "NoSuchElementException while handling the request" }
            ctx.status(404)
        }
        app.exception(IOException::class.java) { e, ctx ->
            logger.error(e) { "IOException while handling the request" }
            ctx.status(500)
            ctx.result(e.message ?: "Internal Server Error")
        }

        app.exception(IllegalArgumentException::class.java) { e, ctx ->
            logger.error(e) { "IllegalArgumentException while handling the request" }
            ctx.status(400)
            ctx.result(e.message ?: "Bad Request")
        }

        app.start()
    }

    // private fun getOpenApiOptions(): OpenApiOptions {
    //     val applicationInfo =
    //         Info().apply {
    //             version("1.0")
    //             description("Suwayomi-Server Api")
    //         }
    //     return OpenApiOptions(applicationInfo).apply {
    //         path("/api/openapi.json")
    //         swagger(
    //             SwaggerOptions("/api/swagger-ui").apply {
    //                 title("Suwayomi-Server Swagger Documentation")
    //             },
    //         )
    //     }
    // }
}
