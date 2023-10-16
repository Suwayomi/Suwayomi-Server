package eu.kanade.tachiyomi.network.interceptor

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CFClearance.resolveWithWebView
import mu.KotlinLogging
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

class CloudflareInterceptor : Interceptor {
    private val logger = KotlinLogging.logger {}

    private val network: NetworkHelper by injectLazy()

    @Suppress("UNUSED_VARIABLE", "UNREACHABLE_CODE")
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        logger.trace { "CloudflareInterceptor is being used." }

        val originalResponse = chain.proceed(chain.request())

        // Check if Cloudflare anti-bot is on
        if (!(originalResponse.code in ERROR_CODES && originalResponse.header("Server") in SERVER_CHECK)) {
            return originalResponse
        }

        throw IOException("playwrite is diabled for v0.6.7")

        logger.debug { "Cloudflare anti-bot is on, CloudflareInterceptor is kicking in..." }

        return try {
            originalResponse.close()
            network.cookieStore.remove(originalRequest.url.toUri())

            val request = resolveWithWebView(originalRequest)

            chain.proceed(request)
        } catch (e: Exception) {
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
            // we don't crash the entire app
            throw IOException(e)
        }
    }

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private val COOKIE_NAMES = listOf("cf_clearance")
    }
}

/*
 * This class is ported from https://github.com/vvanglro/cf-clearance
 * The original code is licensed under Apache 2.0
*/
object CFClearance {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()

    init {
        // Fix the default DriverJar issue by providing our own implementation
        // ref: https://github.com/microsoft/playwright-java/issues/1138
        System.setProperty("playwright.driver.impl", "suwayomi.tachidesk.server.util.DriverJar")
    }

    fun resolveWithWebView(originalRequest: Request): Request {
        val url = originalRequest.url.toString()

        logger.debug { "resolveWithWebView($url)" }

        val cookies =
            Playwright.create().use { playwright ->
                playwright.chromium().launch(
                    LaunchOptions()
                        .setHeadless(false)
                        .apply {
                            if (serverConfig.socksProxyEnabled.value) {
                                setProxy("socks5://${serverConfig.socksProxyHost.value}:${serverConfig.socksProxyPort.value}")
                            }
                        },
                ).use { browser ->
                    val userAgent = originalRequest.header("User-Agent")
                    if (userAgent != null) {
                        browser.newContext(Browser.NewContextOptions().setUserAgent(userAgent)).use { browserContext ->
                            browserContext.newPage().use { getCookies(it, url) }
                        }
                    } else {
                        browser.newPage().use { getCookies(it, url) }
                    }
                }
            }

        // Copy cookies to cookie store
        cookies.groupBy { it.domain }.forEach { (domain, cookies) ->
            network.cookieStore.addAll(
                url =
                    HttpUrl.Builder()
                        .scheme("http")
                        .host(domain)
                        .build(),
                cookies = cookies,
            )
        }
        // Merge new and existing cookies for this request
        // Find the cookies that we need to merge into this request
        val convertedForThisRequest =
            cookies.filter {
                it.matches(originalRequest.url)
            }
        // Extract cookies from current request
        val existingCookies =
            Cookie.parseAll(
                originalRequest.url,
                originalRequest.headers,
            )
        // Filter out existing values of cookies that we are about to merge in
        val filteredExisting =
            existingCookies.filter { existing ->
                convertedForThisRequest.none { converted -> converted.name == existing.name }
            }
        logger.trace { "Existing cookies" }
        logger.trace { existingCookies.joinToString("; ") }
        val newCookies = filteredExisting + convertedForThisRequest
        logger.trace { "New cookies" }
        logger.trace { newCookies.joinToString("; ") }
        return originalRequest.newBuilder()
            .header("Cookie", newCookies.joinToString("; ") { "${it.name}=${it.value}" })
            .build()
    }

    @Suppress("UNREACHABLE_CODE")
    fun getWebViewUserAgent(): String {
        return try {
            throw PlaywrightException("playwrite is diabled for v0.6.7")

            Playwright.create().use { playwright ->
                playwright.chromium().launch(
                    LaunchOptions()
                        .setHeadless(true),
                ).use { browser ->
                    browser.newPage().use { page ->
                        val userAgent = page.evaluate("() => {return navigator.userAgent}") as String
                        logger.debug { "WebView User-Agent is $userAgent" }
                        return userAgent
                    }
                }
            }
        } catch (e: PlaywrightException) {
            // Playwright might fail on headless environments like docker
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"
        }
    }

    private fun getCookies(
        page: Page,
        url: String,
    ): List<Cookie> {
        applyStealthInitScripts(page)
        page.navigate(url)
        val challengeResolved = waitForChallengeResolve(page)

        return if (challengeResolved) {
            val cookies = page.context().cookies()

            logger.debug {
                val userAgent = page.evaluate("() => {return navigator.userAgent}")
                "Playwright User-Agent is $userAgent"
            }

            // Convert PlayWright cookies to OkHttp cookies
            cookies.map {
                Cookie.Builder()
                    .domain(it.domain.removePrefix("."))
                    .expiresAt(it.expires?.times(1000)?.toLong() ?: Long.MAX_VALUE)
                    .name(it.name)
                    .path(it.path)
                    .value(it.value).apply {
                        if (it.httpOnly) httpOnly()
                        if (it.secure) secure()
                    }.build()
            }
        } else {
            logger.debug { "Cloudflare challenge failed to resolve" }
            throw CloudflareBypassException()
        }
    }

    // ref: https://github.com/vvanglro/cf-clearance/blob/44124a8f06d8d0ecf2bf558a027082ff88dab435/cf_clearance/stealth.py#L18
    private val stealthInitScripts by lazy {
        arrayOf(
            ServerConfig::class.java.getResource("/cloudflare-js/canvas.fingerprinting.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/chrome.global.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/emulate.touch.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/navigator.permissions.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/navigator.webdriver.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/chrome.runtime.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/chrome.plugin.js")!!.readText(),
        )
    }

    // ref: https://github.com/vvanglro/cf-clearance/blob/44124a8f06d8d0ecf2bf558a027082ff88dab435/cf_clearance/stealth.py#L76
    private fun applyStealthInitScripts(page: Page) {
        for (script in stealthInitScripts) {
            page.addInitScript(script)
        }
    }

    // ref: https://github.com/vvanglro/cf-clearance/blob/44124a8f06d8d0ecf2bf558a027082ff88dab435/cf_clearance/retry.py#L21
    private fun waitForChallengeResolve(page: Page): Boolean {
        // sometimes the user has to solve the captcha challenge manually, potentially wait a long time
        val timeoutSeconds = 120
        repeat(timeoutSeconds) {
            page.waitForTimeout(1.seconds.toDouble(DurationUnit.MILLISECONDS))
            val success =
                try {
                    page.querySelector("#challenge-form") == null
                } catch (e: Exception) {
                    logger.debug(e) { "query Error" }
                    false
                }
            if (success) return true
        }
        return false
    }

    private class CloudflareBypassException : Exception()
}
