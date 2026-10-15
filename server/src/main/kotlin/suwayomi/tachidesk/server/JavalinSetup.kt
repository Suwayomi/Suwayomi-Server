package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import gg.jte.ContentType
import gg.jte.TemplateEngine
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.after
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.config.RoutesConfig
import io.javalin.config.SizeUnit
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import io.javalin.http.NotFoundResponse
import io.javalin.http.RedirectResponse
import io.javalin.http.UnauthorizedResponse
import io.javalin.json.JavalinJackson3
import io.javalin.rendering.template.JavalinJte
import io.javalin.websocket.WsContext
import jakarta.servlet.ServletException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import org.eclipse.jetty.server.ServerConnector
import suwayomi.tachidesk.global.GlobalAPI
import suwayomi.tachidesk.graphql.GraphQL
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.i18n.LocalizationHelper
import suwayomi.tachidesk.manga.MangaAPI
import suwayomi.tachidesk.manga.impl.NoChaptersFoundException
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.chapter.SourceException
import suwayomi.tachidesk.manga.impl.util.network.isHostUnreachable
import suwayomi.tachidesk.opds.OpdsAPI
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.UnauthorizedException
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.server.user.getUserFromContext
import suwayomi.tachidesk.server.user.getUserFromWsContext
import suwayomi.tachidesk.server.util.Browser
import suwayomi.tachidesk.server.util.ServerSubpath
import suwayomi.tachidesk.server.util.WebInterfaceManager
import tools.jackson.core.JacksonException
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import org.eclipse.jetty.http.HttpException as JettyHttpException

object JavalinSetup {
    private val logger = KotlinLogging.logger {}

    // Slack on top of the per-file upload cap (Backup.MAX_UPLOAD_SIZE) for the multipart
    // envelope — boundaries, headers and auxiliary parts like GraphQL's "operations"/"map".
    private const val MULTIPART_REQUEST_OVERHEAD_BYTES = 10L * 1024 * 1024 // ~10MB

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun <T> future(block: suspend CoroutineScope.() -> T): CompletableFuture<T> = scope.future(block = block)

    fun javalinSetup() {
        val app =
            Javalin.start { config ->
                val templateEngine = TemplateEngine.createPrecompiled(ContentType.Html)
                config.fileRenderer(JavalinJte(templateEngine))

                config.jsonMapper(JavalinJackson3())

                WebInterfaceManager.setup(config)

                // config.registerPlugin(OpenApiPlugin(getOpenApiOptions()))

                // Multipart uploads are buffered by Jetty before an endpoint gets to check
                // their size — cap the whole request so oversized backup/extension uploads
                // are rejected while being read. The endpoints still enforce the per-file
                // cap (Backup.MAX_UPLOAD_SIZE) themselves to report a proper 413.
                config.jetty.multipartConfig.maxTotalRequestSize(
                    Backup.MAX_UPLOAD_SIZE + MULTIPART_REQUEST_OVERHEAD_BYTES,
                    SizeUnit.BYTES,
                )

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

                                // a failed re-bind must not propagate: the exception would
                                // cancel this subscription job (silently disabling all
                                // further ip/port changes) and leave the connector stopped
                                try {
                                    connector.host = newIp
                                    connector.port = newPort
                                    connector.stop()
                                    connector.start()

                                    logger.info { "Server ip and/or port changed from $oldIp:$oldPort to $newIp:$newPort " }
                                } catch (e: Exception) {
                                    logger.error(
                                        e,
                                    ) { "Failed to rebind the server connector to $newIp:$newPort, staying on $oldIp:$oldPort" }
                                    connector.host = oldIp
                                    connector.port = oldPort
                                    runCatching {
                                        if (!connector.isStarted) {
                                            connector.start()
                                        }
                                    }.onFailure {
                                        logger.error(it) { "Failed to restart the connector on the previous address $oldIp:$oldPort" }
                                    }
                                }
                            },
                        )
                        connectorAdded = true
                    }
                }

                config.bundledPlugins.enableCors { cors ->
                    cors.addRule {
                        it.path = ServerSubpath.maybeAddAsPrefix("api/*")
                        it.allowCredentials = false
                        it.reflectClientOrigin = true
                    }
                }

                config.routes.defineCore()
                config.routes.apiBuilder {
                    path(ServerSubpath.maybeAddAsPrefix("api/")) {
                        path("v1/") {
                            GlobalAPI.defineEndpoints()
                            MangaAPI.defineEndpoints()
                        }

                        OpdsAPI.defineEndpoints()
                        GraphQL.defineEndpoints()

                        after { ctx ->
                            // If not matched, the request was for an invalid endpoint
                            // Return a 404 instead of redirecting to the UI for usability
                            if (ctx.endpoints().lastHttpEndpoint()?.path == null || ctx.endpoints().lastHttpEndpoint()?.path == "*") {
                                throw NotFoundResponse()
                            }
                        }
                    }
                }

                config.events.serverStarted {
                    val addresses =
                        try {
                            when (serverConfig.ip.value) {
                                "0.0.0.0" -> listOf("127.0.0.1") + getIPv4Addresses()
                                else -> listOf("127.0.0.1", serverConfig.ip.value)
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "Error getting IPv4 addresses" }
                            listOf("127.0.0.1")
                        }

                    logger.info {
                        val port = serverConfig.port.value
                        buildString {
                            appendLine("Server is available at:")

                            addresses
                                .distinct()
                                .forEach { address ->
                                    appendLine("  http://$address:$port")
                                }
                        }.trimEnd()
                    }
                    if (serverConfig.initialOpenInBrowserEnabled.value) {
                        scope.launch {
                            withTimeoutOrNull(10.seconds) {
                                WebInterfaceManager.isSetupComplete.first { it }
                            }
                            Browser.openInBrowser()
                        }
                    }
                }
            }

        // when JVM is prompted to shutdown, stop javalin gracefully
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                app.stop()
            },
        )
    }

    /**
     * Validates the request's Basic credentials against the configured auth credentials.
     *
     * Always returns false when the configured credentials are blank (the default): without
     * this guard, the empty credentials `user:pass` -> `:` would compare equal to the empty
     * defaults and be accepted as valid, which [UserType.requireUserWithBasicFallback] would
     * then treat as a reason to escalate to the built-in admin user.
     */
    internal fun credentialsValid(ctx: Context): Boolean {
        if (serverConfig.authUsername.value.isBlank() || serverConfig.authPassword.value.isBlank()) {
            return false
        }

        val basicAuthCredentials = ctx.basicAuthCredentials() ?: return false
        val (username, password) = basicAuthCredentials
        return username == serverConfig.authUsername.value &&
            password == serverConfig.authPassword.value
    }

    fun RoutesConfig.defineCore() {
        val loginPath = ServerSubpath.maybeAddAsPrefix("/login.html")
        val pingPath = ServerSubpath.maybeAddAsPrefix("/api/v1/ping")

        // unauthenticated liveness/identity probe (used by AppMutex to detect a running
        // instance regardless of the configured authMode); intentionally exposes nothing
        // beyond "this is a Suwayomi-Server"
        get(pingPath) { ctx ->
            ctx.contentType("text/plain")
            ctx.result("pong")
        }

        get(loginPath) { ctx ->
            val locale: Locale = LocalizationHelper.ctxToLocale(ctx)
            ctx.header("content-type", "text/html")
            val httpCacheSeconds = 1.days.inWholeSeconds
            ctx.header("cache-control", "max-age=$httpCacheSeconds")
            ctx.render(
                "Login.jte",
                mapOf(
                    "locale" to locale,
                    "error" to "",
                ),
            )
        }

        post(loginPath) { ctx ->
            // Evaluated and rejected for the self-hosted threat model: no rate limiting,
            // no post-login session rotation, no CSRF token on this form. The session is
            // a same-site JSESSIONID cookie and Suwayomi is intended for a trusted network;
            // hardening here is out of scope for a hygiene batch (recorded in CHANGELOG).
            val username = ctx.formParam("user")
            val password = ctx.formParam("pass")
            // reject outright when no credentials are configured: otherwise submitting the
            // empty form matches the empty defaults and logs in without any credentials
            val isValid =
                serverConfig.authUsername.value.isNotBlank() &&
                    serverConfig.authPassword.value.isNotBlank() &&
                    username == serverConfig.authUsername.value &&
                    password == serverConfig.authPassword.value

            if (isValid) {
                val redirect = ctx.queryParam("redirect") ?: ServerSubpath.maybeAddAsPrefix("/")
                // only plain same-site relative paths: must start with a single '/' — the
                // second char must not be '/' or '\' (network-path reference `//evil.com`
                // and the WHATWG `\`-as-`/` quirk like `\evil.com`), and the value must not
                // contain '\' or a NUL escape anywhere. ASCII control characters and DEL are
                // rejected too: the WHATWG URL parser silently strips TAB/LF/CR, so a header
                // value like "/\t/evil.com" would be followed as "//evil.com"
                val isSafeRedirect =
                    redirect.startsWith('/') &&
                        (redirect.length == 1 || (redirect[1] != '/' && redirect[1] != '\\')) &&
                        !redirect.contains('\\') &&
                        !redirect.contains("%00", ignoreCase = true) &&
                        redirect.none { it <= ' ' || it == '\u007f' }
                if (!isSafeRedirect) {
                    throw BadRequestResponse("Given redirect is not a relative path, refusing")
                }
                // NOTE: We currently have no session handler attached.
                // Thus, all sessions are stored in memory and not persisted.
                // Furthermore, default session timeout appears to be 30m
                ctx.header("Location", redirect)
                ctx.sessionAttribute("logged-in", username)
                throw RedirectResponse(HttpStatus.SEE_OTHER)
            }

            val locale: Locale = LocalizationHelper.ctxToLocale(ctx)
            ctx.header("content-type", "text/html")
            ctx.req().session.invalidate()
            ctx.render(
                "Login.jte",
                mapOf(
                    "locale" to locale,
                    "error" to "Invalid username or password",
                ),
            )
        }

        beforeMatched { ctx ->
            // only exact root-level paths are auth-exempt (a suffix match would also let
            // e.g. "/api/v1/…site.webmanifest" slip through)
            val isWebManifest =
                listOf("site.webmanifest", "manifest.json", "login.html").any {
                    ctx.path() == ServerSubpath.maybeAddAsPrefix("/$it")
                }
            val isPageIcon =
                ctx
                    .path()
                    // strip a configured webUISubpath first so "/s/favicon.ico" still
                    // counts as a page icon (the leftover must be a single segment)
                    .removePrefix(ServerSubpath.normalized())
                    .removePrefix("/")
                    .let {
                        !it.contains('/') && listOf(".png", ".jpg", ".ico").any(it::endsWith)
                    }
            val isPing = ctx.path() == ServerSubpath.maybeAddAsPrefix("/api/v1/ping")
            val isPreFlight = ctx.method() == HandlerType.OPTIONS
            val isApi = ctx.path().startsWith(ServerSubpath.maybeAddAsPrefix("/api/"))

            // Design decision: /api/ paths are intentionally NOT hard-blocked for visitors
            // here — GraphQL exposes anonymous operations (login, refreshToken, redeem
            // codes) that must stay reachable, so endpoint-level `requireUser`/
            // `requirePermissions` checks are the only line of defense.
            val requiresAuthentication = !isPreFlight && !isPageIcon && !isWebManifest && !isPing
            if (!requiresAuthentication) {
                return@beforeMatched
            }

            val authMode = serverConfig.authMode.value

            fun cookieValid(): Boolean {
                val username = ctx.sessionAttribute<String>("logged-in") ?: return false
                return serverConfig.authUsername.value.isNotBlank() &&
                    username == serverConfig.authUsername.value
            }

            if (authMode == AuthMode.SIMPLE_LOGIN && !cookieValid() && !isApi) {
                val url =
                    "$loginPath?redirect=" +
                        URLEncoder.encode(ctx.path() + (ctx.queryString()?.let { "?" + it } ?: ""), Charsets.UTF_8)
                ctx.header("Location", url)
                throw RedirectResponse(HttpStatus.SEE_OTHER)
            }

            if (authMode == AuthMode.BASIC_AUTH && !credentialsValid(ctx)) {
                ctx.header("WWW-Authenticate", "Basic")
                throw UnauthorizedResponse()
            }

            ctx.setAttribute(Attribute.TachideskUser, runBlocking { getUserFromContext(ctx) })
            ctx.setAttribute(Attribute.TachideskBasic, credentialsValid(ctx))
        }

        wsBeforeUpgrade { ctx ->
            val authMode = serverConfig.authMode.value

            if (authMode == AuthMode.BASIC_AUTH && !credentialsValid(ctx)) {
                ctx.header("WWW-Authenticate", "Basic")
                throw UnauthorizedResponse()
            }
        }

        wsBefore {
            it.onConnect { ctx ->
                ctx.setAttribute(Attribute.TachideskUser, runBlocking { getUserFromWsContext(ctx) })
            }
        }

        defineExceptionHandlers()
    }

    fun RoutesConfig.defineExceptionHandlers() {
        // Legacy "not found" signaling: a lot of impl code still surfaces missing entities
        // as NPE/`first()`-style failures. Keep the 404 mapping for those; new code should
        // throw io.javalin.http.NotFoundResponse instead.
        exception(NullPointerException::class.java) { e, ctx ->
            logger.error(e) { "NullPointerException while handling the request" }
            ctx.status(404)
        }
        exception(NoSuchElementException::class.java) { e, ctx ->
            logger.error(e) { "NoSuchElementException while handling the request" }
            ctx.status(404)
        }
        // a manga whose source has no chapters is a missing resource, not a server fault
        exception(NoChaptersFoundException::class.java) { e, ctx ->
            logger.warn { "No chapters found while handling ${ctx.path()}: ${e.message}" }
            ctx.status(404)
        }
        // business errors thrown by sources/extensions (permission denied, paywalled
        // chapters, …) are upstream faults — the message is the source's own and is
        // what the user needs to act on
        exception(SourceException::class.java) { e, ctx ->
            logger.warn { "Source rejected the request while handling ${ctx.path()}: ${e.message}" }
            ctx.sourceFailed(e.message)
        }

        // malformed request bodies are client errors, not server faults:
        // kotlinx.serialization decode failures (SerializationException is an
        // IllegalArgumentException and would already hit the 400 mapping below) and
        // Jackson3 failures from ctx.bodyAsClass / JsonMapper.fromJsonString
        exception(SerializationException::class.java) { e, ctx ->
            logger.debug(e) { "Request body could not be deserialized (kotlinx.serialization)" }
            ctx.status(400)
            ctx.result("Bad Request")
        }
        exception(JacksonException::class.java) { e, ctx ->
            logger.debug(e) { "Request body could not be deserialized (jackson)" }
            ctx.status(400)
            ctx.result("Bad Request")
        }

        exception(IOException::class.java) { e, ctx ->
            if (e.isHostUnreachable()) {
                // A source host being down isn't a server fault
                logger.warn { "Source host unreachable while handling ${ctx.path()}: ${e.message}" }
                ctx.sourceFailed(e.message)
                return@exception
            }

            // don't echo internal exception details to the client
            logger.error(e) { "IOException while handling the request" }
            ctx.status(500)
            ctx.result("Internal Server Error")
        }

        exception(HttpException::class.java) { e, ctx ->
            // A source error isn't a server fault, and its stack trace only shows obfuscated extension code
            logger.warn { "Source answered HTTP ${e.code} while handling ${ctx.path()}" }
            ctx.sourceFailed(e.message)
        }

        exception(IllegalArgumentException::class.java) { e, ctx ->
            logger.error(e) { "IllegalArgumentException while handling the request" }
            ctx.status(400)
            ctx.result("Bad Request")
        }

        exception(UnauthorizedException::class.java) { e, ctx ->
            logger.error(e) { "UnauthorizedException while handling the request" }
            ctx.status(HttpStatus.UNAUTHORIZED)
            ctx.result(e.message ?: "Unauthorized")
        }

        exception(ForbiddenException::class.java) { e, ctx ->
            logger.error(e) { "ForbiddenException while handling the request" }
            ctx.status(HttpStatus.FORBIDDEN)
            ctx.result(e.message ?: "Forbidden")
        }

        exception(ServletException::class.java) { e, ctx ->
            logger.error(e) { "Jakarta Exception while handling the request" }
            val rootCause = e.rootCause
            if (rootCause is JettyHttpException) {
                ctx.status(rootCause.code)
                ctx.result(rootCause.reason ?: "Forbidden")
            } else {
                ctx.status(500)
            }
        }
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

    sealed class Attribute<T : Any>(
        val name: String,
    ) {
        data object TachideskUser : Attribute<UserType>("user")

        data object TachideskBasic : Attribute<Boolean>("basicAuthValid")
    }

    // 424 rather than 502: clients and proxies read 502 to 504 as the server being down
    private fun Context.sourceFailed(message: String?) {
        status(HttpStatus.FAILED_DEPENDENCY)
        result(message ?: "Source request failed")
    }

    private fun <T : Any> Context.setAttribute(
        attribute: Attribute<T>,
        value: T,
    ) {
        attribute(attribute.name, value)
    }

    private fun <T : Any> WsContext.setAttribute(
        attribute: Attribute<T>,
        value: T,
    ) {
        attribute(attribute.name, value)
    }

    fun <T : Any> Context.getAttribute(attribute: Attribute<T>): T = attribute(attribute.name)!!

    fun <T : Any> WsContext.getAttribute(attribute: Attribute<T>): T = attribute(attribute.name)!!

    fun <T : Any> WsContext.getAttributeOrSet(
        attribute: Attribute<T>,
        replaceIf: (T) -> Boolean = { false },
        set: () -> T,
    ): T {
        var item: T? = attribute(attribute.name)

        if (item != null && replaceIf(item)) {
            item = null
        }

        return item ?: set().also { setAttribute(attribute, it) }
    }

    fun getIPv4Addresses(): List<String> =
        NetworkInterface
            .getNetworkInterfaces()
            .asSequence()
            .flatMap { networkInterface ->
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    return@flatMap emptySequence()
                }

                networkInterface.inetAddresses
                    .asSequence()
                    .filterIsInstance<Inet4Address>()
                    .map { address -> address.hostAddress }
            }.toList()
}
