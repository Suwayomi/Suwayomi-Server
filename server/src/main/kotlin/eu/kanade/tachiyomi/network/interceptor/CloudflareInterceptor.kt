package eu.kanade.tachiyomi.network.interceptor

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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.buildSocksProxyUrl
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class CloudflareInterceptor(
    /**
     * Records the User-Agent a successful bypass solved the challenge with,
     * per request host. `cf_clearance` is bound to the solving UA, so requests
     * to that host must keep sending it — but one host's bypass must not
     * stomp the global default UA (and with it every registered source).
     */
    private val setUserAgentForHost: (host: String, userAgent: String) -> Unit,
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
            // close before throwing: a leaked CF 403/503 response leaks the
            // connection on every challenge hit
            originalResponse.close()
            throw IOException("Cloudflare bypass currently disabled")
        }

        // FlareSolverr only supports `request.get`/`request.post`, and a POST
        // body is serialized into the bypass payload. A one-shot body would be
        // consumed here and the retried request would then fail — give up on
        // the bypass instead of corrupting the request.
        val originalBody = originalRequest.body
        if ((originalRequest.method != "GET" && originalRequest.method != "POST") ||
            originalBody?.isOneShot() == true
        ) {
            originalResponse.close()
            throw IOException(
                "Cloudflare bypass not possible for ${originalRequest.method} request " +
                    "with ${if (originalBody?.isOneShot() == true) "a one-shot" else "no"} body",
            )
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

                // Detection by protocol fields, not message text: a solved
                // challenge yields a clearance cookie — the same condition
                // requestWithFlareSolverr checks downstream.
                val cloudflareDetected =
                    flareResponse.status.equals("ok", ignoreCase = true) &&
                        flareResponse.solution.cookies.any { it.name in COOKIE_NAMES }
                return if (cloudflareDetected) {
                    val request =
                        CFClearance.requestWithFlareSolverr(
                            flareResponse,
                            setUserAgentForHost,
                            originalRequest,
                        )
                    bypassRequest.complete(
                        CFClearance.Result.CloudflareBypassed(
                            flareResponse.solution.userAgent,
                        ),
                    )

                    chain.proceed(request)
                } else {
                    logger.debug {
                        "FlareSolverr reported no solved challenge for $host (status=${flareResponse.status}, message=${flareResponse.message})"
                    }
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

                setUserAgentForHost(originalRequest.url.host, flareResponse.solution.userAgent)

                // Synthesize the response from the solver's own metadata: the
                // original 403 headers (content-length/type of the CF error
                // page) don't describe the solver body. Content-Length is
                // dropped so OkHttp derives it from the new body.
                val solutionHeaders =
                    Headers
                        .Builder()
                        .apply {
                            flareResponse.solution.headers.orEmpty().forEach { (name, value) ->
                                if (!name.equals("Content-Length", ignoreCase = true)) {
                                    add(name, value)
                                }
                            }
                        }.build()
                val contentType =
                    flareResponse.solution.headers
                        .orEmpty()
                        .entries
                        .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                        ?.value
                        ?.toMediaTypeOrNull()

                return originalResponse
                    .newBuilder()
                    .code(flareResponse.solution.status)
                    .headers(solutionHeaders)
                    .body(flareResponse.solution.response.toResponseBody(contentType))
                    .build()
            } else {
                logger.debug { "FlareSolverr response is an image html template, not falling back" }
            }
        }

        val request =
            CFClearance.requestWithFlareSolverr(flareResponse, setUserAgentForHost, originalRequest)

        return chain.proceed(request)
    }

    private fun awaitInflightResult(future: CompletableFuture<CFClearance.Result>): CFClearance.Result {
        // Bounded wait: an unbounded get() would hang this request forever if the
        // in-flight bypass never completes (the TimeoutException catch below was
        // dead code before because get() had no timeout).
        val timeoutMillis =
            serverConfig.flareSolverrTimeout.value.seconds
                .plus(15.seconds)
                .inWholeMilliseconds
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw IOException("Timed out waiting for the in-flight Cloudflare bypass", e)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        val COOKIE_NAMES = listOf("cf_clearance")
        private val CHROME_IMAGE_TEMPLATE_REGEX = Regex("""<title>(.*?) \(\d+×\d+\)</title>""")
    }
}

/*
 * This class is ported from https://github.com/vvanglro/cf-clearance
 * The original code is licensed under Apache 2.0
*/
object CFClearance {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()
    private val client by lazy {
        @Suppress("OPT_IN_USAGE")
        serverConfig.flareSolverrTimeout
            .map { timeoutInt ->
                val timeout = timeoutInt.seconds
                network.client
                    .newBuilder()
                    .callTimeout(timeout.plus(10.seconds).toJavaDuration())
                    .readTimeout(timeout.plus(5.seconds).toJavaDuration())
                    .build()
            }.stateIn(GlobalScope, SharingStarted.Eagerly, network.client)
    }
    private val json: Json by injectLazy()
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * FlareSolverr calls are serialized per target host (not globally):
     * bypasses for different sites may proceed in parallel, while two bypasses
     * for the same host cannot stampede the solver. The map is bounded by the
     * number of distinct Cloudflare-protected hosts seen — small in practice.
     */
    private val hostMutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(host: String): Mutex = hostMutexes.computeIfAbsent(host) { Mutex() }

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
    data class FlareSolverProxy(
        val url: String,
    )

    @Serializable
    data class FlareSolverRequest(
        val cmd: String,
        val url: String,
        val maxTimeout: Int? = null,
        val session: String? = null,
        @SerialName("session_ttl_minutes")
        val sessionTtlMinutes: Int? = null,
        val cookies: List<FlareSolverCookie>? = null,
        val returnOnlyCookies: Boolean? = null,
        val proxy: FlareSolverProxy? = null,
        val postData: String? = null, // only used with cmd 'request.post'
    )

    internal fun FlareSolverRequest.withRequestProxy(proxyUrl: String?): FlareSolverRequest =
        proxyUrl?.let { copy(session = null, sessionTtlMinutes = null, proxy = FlareSolverProxy(it)) } ?: this

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
        val socksProxy = buildSocksProxyUrl()
        if (socksProxy != null) {
            // withRequestProxy() clears session/sessionTtlMinutes below: a
            // FlareSolverr browser session cannot be pinned to a specific
            // proxy, so session reuse is disabled for proxied calls.
            logger.debug { "SOCKS proxy configured: FlareSolverr session reuse is disabled for this request" }
        }

        return with(json) {
            mutexFor(originalRequest.url.host).withLock {
                client.value
                    .newCall(
                        POST(
                            url = serverConfig.flareSolverrUrl.value.removeSuffix("/") + "/v1",
                            headers = socksProxy?.let { Headers.headersOf("X-Proxy-Server", it) } ?: Headers.headersOf(),
                            // Trust boundary: the FlareSolverr endpoint (serverConfig.flareSolverrUrl —
                            // typically a third-party container) receives the full request URL, every
                            // cookie this host would get (minus cf_clearance), and — for POST — the
                            // request body. Only point it at hosts you trust it with.
                            body =
                                Json
                                    .encodeToString(
                                        FlareSolverRequest(
                                            "request.${originalRequest.method.lowercase()}",
                                            originalRequest.url.toString(),
                                            session = serverConfig.flareSolverrSessionName.value,
                                            sessionTtlMinutes = serverConfig.flareSolverrSessionTtl.value,
                                            cookies =
                                                network.cookieStore
                                                    .get(originalRequest.url)
                                                    .filter { it.name !in CloudflareInterceptor.COOKIE_NAMES }
                                                    .map { cookie ->
                                                        FlareSolverCookie(cookie.name, cookie.value)
                                                    },
                                            returnOnlyCookies = onlyCookies,
                                            maxTimeout = timeout.inWholeMilliseconds.toInt(),
                                            postData = originalRequest.flareSolverrPostData(),
                                        ).withRequestProxy(socksProxy),
                                    ).toRequestBody(jsonMediaType),
                        ),
                    ).awaitSuccess()
                    .parseAs<FlareSolverResponse>()
            }
        }
    }

    /**
     * The request body to hand to FlareSolverr for `request.post`, or null when
     * it should not be forwarded. Only replayable, textual bodies are safe to
     * serialize: a binary body would be corrupted by readUtf8() and a one-shot
     * body is already rejected by the interceptor before we get here.
     */
    private fun Request.flareSolverrPostData(): String? {
        if (method != "POST") return null
        val body = body ?: return null
        val contentType = body.contentType()
        val isTextual =
            contentType == null ||
                contentType.type == "text" ||
                contentType.subtype.let {
                    "json" in it || "xml" in it || it == "x-www-form-urlencoded" || it == "javascript"
                }
        if (!isTextual) return null
        return Buffer()
            .also { body.writeTo(it) }
            .readUtf8()
    }

    fun requestWithFlareSolverr(
        flareSolverResponse: FlareSolverResponse,
        setUserAgentForHost: (host: String, userAgent: String) -> Unit,
        originalRequest: Request,
    ): Request {
        if (flareSolverResponse.solution.cookies.none { it.name in CloudflareInterceptor.COOKIE_NAMES }) {
            logger.debug { "Cloudflare challenge failed to resolve" }
            throw CloudflareBypassException()
        } else {
            setUserAgentForHost(originalRequest.url.host, flareSolverResponse.solution.userAgent)
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
        }
    }

    private class CloudflareBypassException : Exception()
}
