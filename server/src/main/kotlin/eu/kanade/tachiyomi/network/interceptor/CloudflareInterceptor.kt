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
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

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
            if (chain.call().isCanceled()) throw IOException("Canceled")

            val myFuture = CompletableFuture<CFClearance.Result>()
            val inflightRequest = CFClearance.inflightCalls.putIfAbsent(host, myFuture)

            val awaitInflightResult = inflightRequest != null
            if (awaitInflightResult) {
                logger.debug { "Waiting for inflight call for host $host" }

                when (val result = awaitInflightResult(inflightRequest, chain)) {
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
                    myFuture.complete(
                        CFClearance.Result.CloudflareBypassed(
                            flareResponse.solution.userAgent,
                        ),
                    )

                    chain.proceed(request)
                } else {
                    CFClearance.inflightCalls.remove(host, myFuture)
                    myFuture.complete(CFClearance.Result.CloudflareNotDetected)

                    maybeFallbackToFlareSolverResponse(
                        flareResponse,
                        chain,
                        originalRequest,
                        originalResponse,
                        flareResponseFallback,
                    )
                }
            } catch (e: Exception) {
                myFuture.completeExceptionally(e)
                throw e
            } finally {
                CFClearance.inflightCalls.remove(host, myFuture)
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

    private fun awaitInflightResult(
        future: CompletableFuture<CFClearance.Result>,
        chain: Interceptor.Chain,
    ): CFClearance.Result {
        while (true) {
            if (chain.call().isCanceled()) throw IOException("Canceled")

            try {
                return future.get(500.milliseconds.inWholeMilliseconds, TimeUnit.MILLISECONDS)
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
        private val COOKIE_NAMES = listOf("cf_clearance")
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
    private val mutex = Mutex()

    sealed class Result {
        data class CloudflareBypassed(
            val userAgent: String,
        ) : Result()

        data object CloudflareNotDetected : Result()
    }

    val inflightCalls = ConcurrentHashMap<String, CompletableFuture<Result>>()

    fun buildRequestWithStoredCookies(
        originalRequest: Request,
        userAgent: String,
    ): Request {
        val finalCookies =
            network.cookieStore.get(originalRequest.url).joinToString("; ", postfix = "; ") {
                "${it.name}=${it.value}"
            }

        return originalRequest
            .newBuilder()
            .header("Cookie", finalCookies)
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
        val url: String,
        val maxTimeout: Int? = null,
        val session: String? = null,
        @SerialName("session_ttl_minutes")
        val sessionTtlMinutes: Int? = null,
        val cookies: List<FlareSolverCookie>? = null,
        val returnOnlyCookies: Boolean? = null,
        val proxy: String? = null,
        val postData: String? = null, // only used with cmd 'request.post'
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
                client.value
                    .newCall(
                        POST(
                            url = serverConfig.flareSolverrUrl.value.removeSuffix("/") + "/v1",
                            body =
                                Json
                                    .encodeToString(
                                        FlareSolverRequest(
                                            "request.${originalRequest.method.lowercase()}",
                                            originalRequest.url.toString(),
                                            session = serverConfig.flareSolverrSessionName.value,
                                            sessionTtlMinutes = serverConfig.flareSolverrSessionTtl.value,
                                            cookies =
                                                network.cookieStore.get(originalRequest.url).map {
                                                    FlareSolverCookie(it.name, it.value)
                                                },
                                            returnOnlyCookies = onlyCookies,
                                            maxTimeout = timeout.inWholeMilliseconds.toInt(),
                                            postData =
                                                if (originalRequest.method == "POST") {
                                                    when (val body = originalRequest.body) {
                                                        is FormBody -> {
                                                            Buffer()
                                                                .also { body.writeTo(it) }
                                                                .readUtf8()
                                                        }

                                                        else -> {
                                                            ""
                                                        }
                                                    }
                                                } else {
                                                    null
                                                },
                                        ),
                                    ).toRequestBody(jsonMediaType),
                        ),
                    ).awaitSuccess()
                    .parseAs<FlareSolverResponse>()
            }
        }
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
            val finalCookies =
                network.cookieStore.get(originalRequest.url).joinToString("; ", postfix = "; ") {
                    "${it.name}=${it.value}"
                }
            logger.trace { "Final cookies\n$finalCookies" }
            return originalRequest
                .newBuilder()
                .header("Cookie", finalCookies)
                .header("User-Agent", flareSolverResponse.solution.userAgent)
                .build()
        } else {
            logger.debug { "Cloudflare challenge failed to resolve" }
            throw CloudflareBypassException()
        }
    }

    private class CloudflareBypassException : Exception()
}
