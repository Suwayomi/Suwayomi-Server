package eu.kanade.tachiyomi.network.interceptor

import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import eu.kanade.tachiyomi.network.NetworkHelper
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

// from TachiWeb-Server
class CloudflareInterceptor : Interceptor {
    private val logger = KotlinLogging.logger {}

    private val network: NetworkHelper by injectLazy()

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        logger.trace { "CloudflareInterceptor is being used." }

        val originalResponse = chain.proceed(chain.request())

        // Check if Cloudflare anti-bot is on
        if (!(originalResponse.code in ERROR_CODES && originalResponse.header("Server") in SERVER_CHECK)) {
            return originalResponse
        }

        logger.debug { "Cloudflare anti-bot is on, CloudflareInterceptor is kicking in..." }

        return try {
            originalResponse.close()
            network.cookies.remove(originalRequest.url.toUri())

            val request = resolveWithWebView(originalRequest)

            chain.proceed(request)
        } catch (e: Exception) {
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
            // we don't crash the entire app
            throw IOException(e)
        }
    }

    private fun resolveWithWebView(originalRequest: Request): Request {
        val url = originalRequest.url.toString()

        logger.debug { "resolveWithWebView($url)" }

        val cookies = Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(
                LaunchOptions()
                    .setHeadless(false)
                    .apply {
                        if (serverConfig.socksProxyEnabled) {
                            setProxy("socks5://${serverConfig.socksProxyHost}:${serverConfig.socksProxyPort}")
                        }
                    }
            )
            val page = browser.newPage()
            stealthInitScripts(page)
            page.navigate(url)

            val res = cloudflareRetry(page)
//            page.waitForClose(WaitForCloseOptions().setTimeout(0.0), {})
            if (res) {
                val cookies = page.context().cookies()

//                val ua = page.evaluate("() => {return navigator.userAgent}")
//                println(ua)
                cookies.map {
                    // Convert cookies -> OkHttp format
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
                logger.debug { "cloudflare challenge failed" }
                throw CloudflareBypassException()
            }
        }

        // Copy cookies to cookie store
        cookies.forEach {
            network.cookies.addAll(
                HttpUrl.Builder()
                    .scheme("http")
                    .host(it.domain)
                    .build(),
                listOf(it)
            )
        }
        // Merge new and existing cookies for this request
        // Find the cookies that we need to merge into this request
        val convertedForThisRequest = cookies.filter {
            it.matches(originalRequest.url)
        }
        // Extract cookies from current request
        val existingCookies = Cookie.parseAll(
            originalRequest.url,
            originalRequest.headers
        )
        // Filter out existing values of cookies that we are about to merge in
        val filteredExisting = existingCookies.filter { existing ->
            convertedForThisRequest.none { converted -> converted.name == existing.name }
        }
        println("existing")
        println(existingCookies.map { it.toString() }.joinToString("; "))
        println("converted")
        println(convertedForThisRequest.map { it.toString() }.joinToString("; "))
        val newCookies = filteredExisting + convertedForThisRequest
        println(newCookies.map { it.toString() }.joinToString("; "))
        return originalRequest.newBuilder()
            .header("Cookie", newCookies.map { "${it.name}=${it.value}" }.joinToString("; "))
            .build()
    }

    val cfScripts by lazy {
        arrayOf(
            ServerConfig::class.java.getResource("/cloudflare-js/canvas.fingerprinting.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/chrome.global.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/emulate.touch.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/navigator.permissions.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/navigator.webdriver.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/chrome.runtime.js")!!.readText(),
            ServerConfig::class.java.getResource("/cloudflare-js/chrome.plugin.js")!!.readText()
        )
    }

    // ref: https://github.com/vvanglro/cf-clearance/blob/44124a8f06d8d0ecf2bf558a027082ff88dab435/cf_clearance/stealth.py#L76
    fun stealthInitScripts(page: Page) {
        for (script in cfScripts) {
            page.addInitScript(script)
        }
    }

    // ref: https://github.com/vvanglro/cf-clearance/blob/44124a8f06d8d0ecf2bf558a027082ff88dab435/cf_clearance/retry.py#L21
    fun cloudflareRetry(page: Page, tries: Int = 30): Boolean {
        for (i in 0 until tries) {
            page.waitForTimeout(1000.0)
            val success = try {
                page.querySelector("#challenge-form") == null
            } catch (e: Exception) {
                false
            }
            if (success) return true
        }
        return false
    }

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private val COOKIE_NAMES = listOf("cf_clearance")
    }

    private class CloudflareBypassException : Exception()
}
