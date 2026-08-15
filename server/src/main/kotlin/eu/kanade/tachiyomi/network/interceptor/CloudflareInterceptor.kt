package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import suwayomi.tachidesk.server.network.SocksProxyManager
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private typealias FlareSolverCommandSender =
    suspend (String, CFClearance.FlareSolverRequest) -> CFClearance.FlareSolverCommandResponse

class CloudflareInterceptor(
    private val setUserAgent: (String) -> Unit,
) : Interceptor {
    private val logger = KotlinLogging.logger {}

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        logger.trace { "CloudflareInterceptor is being used." }

        val originalResponse = chain.proceed(originalRequest)

        // Check if Cloudflare anti-bot is on
        if (!(originalResponse.code in ERROR_CODES && originalResponse.header("Server") in SERVER_CHECK)) {
            return originalResponse
        }

        if (!serverConfig.flareSolverrEnabled.value) {
            throw IOException("Cloudflare bypass currently disabled")
        }

        logger.debug { "Cloudflare anti-bot is on, CloudflareInterceptor is kicking in..." }

        val flareResponseFallback = serverConfig.flareSolverrAsResponseFallback.value

        return try {
            originalResponse.close()
            resolveCloudflare(chain, originalRequest, originalResponse, flareResponseFallback)
        } catch (e: Exception) {
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that we don't crash the entire app
            throw IOException(e)
        }
    }

    private fun resolveCloudflare(
        chain: Interceptor.Chain,
        originalRequest: Request,
        originalResponse: Response,
        flareResponseFallback: Boolean,
    ): Response {
        val host = originalRequest.url.host

        while (true) {
            val bypassRequest = CompletableFuture<CFClearance.Result>()
            val inflightRequest = CFClearance.inflightCalls.putIfAbsent(host, bypassRequest)

            val awaitInflightResult = inflightRequest != null
            if (awaitInflightResult) {
                logger.debug { "Waiting for inflight call for host $host" }

                when (val result = awaitInflightResult(inflightRequest)) {
                    is CFClearance.Result.CloudflareBypassed -> {
                        val request =
                            CFClearance.buildRequestWithStoredCookies(
                                originalRequest,
                                result.userAgent,
                            )

                        return chain.proceed(request)
                    }

                    is CFClearance.Result.CloudflareNotDetected -> {
                        logger.debug { "Inflight call did not detect Cloudflare for $host, retrying" }
                        continue
                    }
                }
            }

            logger.debug { "Calling FlareSolverr for host $host" }
            try {
                val flareResponse =
                    runBlocking {
                        CFClearance.resolveWithFlareSolver(originalRequest, !flareResponseFallback)
                    }

                val cloudflareDetected =
                    !flareResponse.message.contains("not detected", ignoreCase = true)
                return if (cloudflareDetected) {
                    val request =
                        CFClearance.requestWithFlareSolverr(
                            flareResponse,
                            setUserAgent,
                            originalRequest,
                        )
                    bypassRequest.complete(
                        CFClearance.Result.CloudflareBypassed(
                            flareResponse.solution.userAgent,
                        ),
                    )

                    chain.proceed(request)
                } else {
                    CFClearance.inflightCalls.remove(host, bypassRequest)
                    bypassRequest.complete(CFClearance.Result.CloudflareNotDetected)

                    maybeFallbackToFlareSolverResponse(
                        flareResponse,
                        chain,
                        originalRequest,
                        originalResponse,
                        flareResponseFallback,
                    )
                }
            } catch (e: Exception) {
                bypassRequest.completeExceptionally(e)
                throw e
            } finally {
                CFClearance.inflightCalls.remove(host, bypassRequest)
            }
        }
    }

    private fun maybeFallbackToFlareSolverResponse(
        flareResponse: CFClearance.FlareSolverResponse,
        chain: Interceptor.Chain,
        originalRequest: Request,
        originalResponse: Response,
        flareResponseFallback: Boolean,
    ): Response {
        logger.debug { "FlareSolverr failed to detect Cloudflare challenge" }

        if (flareResponseFallback &&
            flareResponse.solution.status in 200..299 &&
            flareResponse.solution.response != null
        ) {
            val isImage =
                flareResponse.solution.response.contains(CHROME_IMAGE_TEMPLATE_REGEX)
            if (!isImage) {
                logger.debug { "Falling back to FlareSolverr response" }

                setUserAgent(flareResponse.solution.userAgent)

                return originalResponse
                    .newBuilder()
                    .code(flareResponse.solution.status)
                    .body(flareResponse.solution.response.toResponseBody())
                    .build()
            } else {
                logger.debug { "FlareSolverr response is an image html template, not falling back" }
            }
        }

        val request =
            CFClearance.requestWithFlareSolverr(flareResponse, setUserAgent, originalRequest)

        return chain.proceed(request)
    }

    private fun awaitInflightResult(future: CompletableFuture<CFClearance.Result>): CFClearance.Result {
        while (true) {
            try {
                return future.get()
            } catch (_: TimeoutException) {
                continue
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
    }

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        val COOKIE_NAMES = listOf("cf_clearance")
        private val CHROME_IMAGE_TEMPLATE_REGEX = Regex("""<title>(.*?) \(\d+×\d+\)</title>""")
    }
}

internal data class FlareSolverSessionSettings(
    val url: String,
    val name: String,
    val ttlMinutes: Int,
    val proxy: CFClearance.FlareSolverProxy?,
)

internal class FlareSolverSessionManager(
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val logger = KotlinLogging.logger {}

    private data class State(
        val settings: FlareSolverSessionSettings,
        val createdAtNanos: Long,
    )

    private var state: State? = null

    suspend fun ensure(
        settings: FlareSolverSessionSettings,
        send: FlareSolverCommandSender,
    ) {
        require(settings.name.isNotBlank()) { "FlareSolverr session name must not be blank" }

        val previous = state
        val expired =
            previous != null &&
                settings.ttlMinutes > 0 &&
                nanoTime() - previous.createdAtNanos >= settings.ttlMinutes.minutes.inWholeNanoseconds
        val mustRecreate = previous == null || previous.settings != settings || expired

        if (mustRecreate) {
            recreate(settings, send)
            return
        }

        // sessions.create is idempotent and restores the proxied session after a FlareSolverr restart.
        send(
            settings.url,
            CFClearance.FlareSolverRequest(
                cmd = "sessions.create",
                session = settings.name,
                proxy = settings.proxy,
            ),
        ).also(::checkCommandResponse)
    }

    suspend fun recreate(
        settings: FlareSolverSessionSettings,
        send: FlareSolverCommandSender,
    ) {
        require(settings.name.isNotBlank()) { "FlareSolverr session name must not be blank" }

        destroy(settings, send)

        send(
            settings.url,
            CFClearance.FlareSolverRequest(
                cmd = "sessions.create",
                session = settings.name,
                proxy = settings.proxy,
            ),
        ).also(::checkCommandResponse)

        state = State(settings, nanoTime())
    }

    suspend fun destroy(
        settings: FlareSolverSessionSettings,
        send: FlareSolverCommandSender,
    ) {
        require(settings.name.isNotBlank()) { "FlareSolverr session name must not be blank" }

        val previous = state
        state = null

        val sessions =
            send(settings.url, CFClearance.FlareSolverRequest(cmd = "sessions.list"))
                .also(::checkCommandResponse)
                .sessions
                .orEmpty()
        val namesToDestroy =
            buildSet {
                add(settings.name)
                previous?.settings?.takeIf { it.url == settings.url }?.let { add(it.name) }
            }

        namesToDestroy.intersect(sessions.toSet()).forEach { sessionName ->
            send(
                settings.url,
                CFClearance.FlareSolverRequest(cmd = "sessions.destroy", session = sessionName),
            ).also(::checkCommandResponse)
        }
    }

    suspend fun <T> execute(
        settings: FlareSolverSessionSettings,
        send: FlareSolverCommandSender,
        retryOnFailure: Boolean,
        request: suspend () -> T,
    ): T {
        ensure(settings, send)

        return try {
            request()
        } catch (e: HttpException) {
            if (e.code != 500) {
                throw e
            }

            if (!retryOnFailure) {
                logger.warn { "FlareSolverr request failed with HTTP 500; invalidating the session without retrying" }
                destroyAfterFailure(settings, send, e)
            }

            // FlareSolverr's timed-out worker can keep controlling a persistent WebDriver.
            // Replacing the session prevents that worker from racing the single retry.
            logger.warn { "FlareSolverr request failed with HTTP 500; recreating the session and retrying once" }
            try {
                recreate(settings, send)
            } catch (recreateError: Exception) {
                recreateError.addSuppressed(e)
                throw recreateError
            }

            try {
                request()
            } catch (retryError: HttpException) {
                retryError.addSuppressed(e)
                if (retryError.code != 500) {
                    throw retryError
                }

                logger.warn { "FlareSolverr retry failed with HTTP 500; invalidating the session" }
                destroyAfterFailure(settings, send, retryError)
            }
        }
    }

    private suspend fun destroyAfterFailure(
        settings: FlareSolverSessionSettings,
        send: FlareSolverCommandSender,
        failure: HttpException,
    ): Nothing {
        try {
            destroy(settings, send)
        } catch (cleanupError: Exception) {
            failure.addSuppressed(cleanupError)
        }

        throw failure
    }

    private fun checkCommandResponse(response: CFClearance.FlareSolverCommandResponse) {
        check(response.status == "ok") { "FlareSolverr session command failed: ${response.message}" }
    }
}

/*
 * This class is ported from https://github.com/vvanglro/cf-clearance
 * The original code is licensed under Apache 2.0
*/
object CFClearance {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()
    private val directClient by lazy {
        network.client
            .newBuilder()
            .proxy(Proxy.NO_PROXY)
            .build()
    }
    private val client by lazy {
        @Suppress("OPT_IN_USAGE")
        serverConfig.flareSolverrTimeout
            .map { timeoutInt ->
                val timeout = timeoutInt.seconds
                directClient
                    .newBuilder()
                    .callTimeout(timeout.plus(10.seconds).toJavaDuration())
                    .readTimeout(timeout.plus(5.seconds).toJavaDuration())
                    .build()
            }.stateIn(GlobalScope, SharingStarted.Eagerly, directClient)
    }
    private val json: Json by injectLazy()
    private val jsonMediaType = "application/json".toMediaType()
    private val mutex = Mutex()
    private val sessionManager = FlareSolverSessionManager()

    sealed class Result {
        data class CloudflareBypassed(
            val userAgent: String,
        ) : Result()

        data object CloudflareNotDetected : Result()
    }

    val inflightCalls = ConcurrentHashMap<String, CompletableFuture<Result>>()

    fun buildRequestWithStoredCookies(
        request: Request,
        userAgent: String,
    ): Request {
        val cookies =
            network.cookieStore.get(request.url).joinToString("; ", postfix = "; ") {
                "${it.name}=${it.value}"
            }

        logger.trace { "Final cookies\n$cookies" }

        return request
            .newBuilder()
            .header("Cookie", cookies)
            .header("User-Agent", userAgent)
            .build()
    }

    @Serializable
    data class FlareSolverCookie(
        val name: String,
        val value: String,
    )

    @Serializable
    data class FlareSolverRequest(
        val cmd: String,
        val url: String? = null,
        val maxTimeout: Int? = null,
        val session: String? = null,
        @SerialName("session_ttl_minutes")
        val sessionTtlMinutes: Int? = null,
        val cookies: List<FlareSolverCookie>? = null,
        val returnOnlyCookies: Boolean? = null,
        val proxy: FlareSolverProxy? = null,
        val postData: String? = null, // only used with cmd 'request.post'
    )

    @Serializable
    data class FlareSolverProxy(
        val url: String,
        val username: String? = null,
        val password: String? = null,
    )

    @Serializable
    data class FlareSolverCommandResponse(
        val status: String,
        val message: String,
        val sessions: List<String>? = null,
    )

    @Serializable
    data class FlareSolverSolutionCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String? = null,
        val expires: Double? = null,
        val size: Int? = null,
        val httpOnly: Boolean? = null,
        val secure: Boolean? = null,
        val session: Boolean? = null,
        val sameSite: String? = null,
    )

    @Serializable
    data class FlareSolverSolution(
        val url: String,
        val status: Int,
        val headers: Map<String, String>? = null,
        val response: String? = null,
        val cookies: List<FlareSolverSolutionCookie>,
        val userAgent: String,
    )

    @Serializable
    data class FlareSolverResponse(
        val solution: FlareSolverSolution,
        val status: String,
        val message: String,
        val startTimestamp: Long,
        val endTimestamp: Long,
        val version: String,
    )

    suspend fun resolveWithFlareSolver(
        originalRequest: Request,
        onlyCookies: Boolean,
    ): FlareSolverResponse {
        val timeout = serverConfig.flareSolverrTimeout.value.seconds
        return with(json) {
            mutex.withLock {
                val sessionSettings = currentSessionSettings()
                val manageSession = sessionSettings.proxy != null && sessionSettings.name.isNotBlank()
                val request =
                    FlareSolverRequest(
                        "request.${originalRequest.method.lowercase()}",
                        originalRequest.url.toString(),
                        session = sessionSettings.name.ifBlank { null },
                        sessionTtlMinutes = sessionSettings.ttlMinutes.takeUnless { manageSession },
                        cookies =
                            network.cookieStore
                                .get(originalRequest.url)
                                .filter { it.name !in CloudflareInterceptor.COOKIE_NAMES }
                                .map { cookie ->
                                    FlareSolverCookie(cookie.name, cookie.value)
                                },
                        returnOnlyCookies = onlyCookies,
                        proxy = sessionSettings.proxy.takeIf { sessionSettings.name.isBlank() },
                        maxTimeout = timeout.inWholeMilliseconds.toInt(),
                        postData =
                            if (originalRequest.method == "POST") {
                                originalRequest.body
                                    ?.let { body ->
                                        Buffer()
                                            .also { body.writeTo(it) }
                                            .readUtf8()
                                    }.orEmpty()
                            } else {
                                null
                            },
                    )

                val sendRequest =
                    suspend {
                        sendFlareSolverRequest(sessionSettings.url, request)
                    }

                if (manageSession) {
                    sessionManager.execute(
                        sessionSettings,
                        ::sendSessionCommand,
                        retryOnFailure = originalRequest.method == "GET",
                        request = sendRequest,
                    )
                } else {
                    sendRequest()
                }
            }
        }
    }

    private suspend fun sendFlareSolverRequest(
        url: String,
        request: FlareSolverRequest,
    ): FlareSolverResponse =
        with(json) {
            client.value
                .newCall(
                    POST(
                        url = url,
                        body = Json.encodeToString(request).toRequestBody(jsonMediaType),
                    ),
                ).awaitSuccess()
                .parseAs<FlareSolverResponse>()
        }

    private fun currentSessionSettings(): FlareSolverSessionSettings {
        val proxySettings = SocksProxyManager.settings.value
        val proxy =
            proxySettings.proxyUrl()?.let { url ->
                FlareSolverProxy(
                    url = url,
                    username = proxySettings.username.ifEmpty { null },
                    password = proxySettings.password.ifEmpty { null },
                )
            }

        return FlareSolverSessionSettings(
            url = serverConfig.flareSolverrUrl.value.removeSuffix("/") + "/v1",
            name = serverConfig.flareSolverrSessionName.value,
            ttlMinutes = serverConfig.flareSolverrSessionTtl.value,
            proxy = proxy,
        )
    }

    private suspend fun sendSessionCommand(
        url: String,
        request: FlareSolverRequest,
    ): FlareSolverCommandResponse =
        with(json) {
            client.value
                .newCall(
                    POST(
                        url = url,
                        body = Json.encodeToString(request).toRequestBody(jsonMediaType),
                    ),
                ).awaitSuccess()
                .parseAs<FlareSolverCommandResponse>()
        }

    fun requestWithFlareSolverr(
        flareSolverResponse: FlareSolverResponse,
        setUserAgent: (String) -> Unit,
        originalRequest: Request,
    ): Request {
        if (flareSolverResponse.solution.status in 200..299) {
            setUserAgent(flareSolverResponse.solution.userAgent)
            val cookies =
                flareSolverResponse.solution.cookies
                    .map { cookie ->
                        Cookie
                            .Builder()
                            .name(cookie.name)
                            .value(cookie.value)
                            .domain(cookie.domain.removePrefix("."))
                            .also {
                                if (cookie.httpOnly != null && cookie.httpOnly) it.httpOnly()
                                if (cookie.secure != null && cookie.secure) it.secure()
                                if (!cookie.path.isNullOrEmpty()) it.path(cookie.path)
                                // We need to convert the expires time to milliseconds for the persistent cookie store
                                if (cookie.expires != null && cookie.expires > 0) it.expiresAt((cookie.expires * 1000).toLong())
                                if (!cookie.domain.startsWith('.')) {
                                    it.hostOnlyDomain(cookie.domain.removePrefix("."))
                                }
                            }.build()
                    }.groupBy { it.domain }
                    .flatMap { (domain, cookies) ->
                        network.cookieStore.addAll(
                            HttpUrl
                                .Builder()
                                .scheme("http")
                                .host(domain.removePrefix("."))
                                .build(),
                            cookies,
                        )

                        cookies
                    }

            logger.trace { "New cookies\n${cookies.joinToString("; ")}" }

            return buildRequestWithStoredCookies(
                request = originalRequest,
                userAgent = flareSolverResponse.solution.userAgent,
            )
        } else {
            logger.debug { "Cloudflare challenge failed to resolve" }
            throw CloudflareBypassException()
        }
    }

    private class CloudflareBypassException : Exception()
}
