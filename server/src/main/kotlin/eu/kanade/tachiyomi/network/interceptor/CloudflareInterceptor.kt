package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CFClearance.resolveWithWebView
import jep.SharedInterpreter
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.TimeUnit

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

        if (!serverConfig.webviewEnabled) {
            throw CloudflareBypassException("Webview is disabled, enable it in server config")
        }

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

    private var undetectedChromeInitialized = false

    fun initializeUndetectedChrome() {
        if (undetectedChromeInitialized) {
            return
        }
        SharedInterpreter().use { jep ->
            val uc = "/home/armor/programming/github-clones/undetected-chromedriver"

            jep.exec("import sys")
            jep.exec("sys.path.insert(0,'$uc')")

            jep.exec("import undetected_chromedriver") // Cache import
        }
        undetectedChromeInitialized = true
    }

    fun resolveWithWebView(originalRequest: Request): Request {
        val url = originalRequest.url.toString()

        logger.debug { "resolveWithWebView($url)" }

        initializeUndetectedChrome()
        val cookies = SharedInterpreter().use { jep ->
            try {
                jep.exec("import undetected_chromedriver as uc")

                jep.exec("options = uc.ChromeOptions()")

                if (serverConfig.socksProxyEnabled) {
                    val proxy = "socks5://${serverConfig.socksProxyHost}:${serverConfig.socksProxyPort}"
                    jep.exec("options.add_argument('--proxy-server=$proxy')")
                }
                jep.exec("driver = uc.Chrome(options=options)")
//                val userAgent = originalRequest.header("User-Agent")

//                if (userAgent != null) {
//                    browser.newContext(Browser.NewContextOptions().setUserAgent(userAgent)).use { browserContext ->
//                        browserContext.newPage().use { getCookies(it, url) }
//                    }
//                } else {
//                    browser.newPage().use { getCookies(it, url) }
//                }
                jep.exec("driver.get('$url')")

                getCookies(jep)
            } finally {
                jep.exec("driver.quit()")
            }
        }

        // Copy cookies to cookie store
        cookies.groupBy { it.domain }.forEach { (domain, cookies) ->
            network.cookies.addAll(
                url = HttpUrl.Builder()
                    .scheme("http")
                    .host(domain)
                    .build(),
                cookies = cookies
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
        logger.trace { "Existing cookies" }
        logger.trace { existingCookies.joinToString("; ") }
        val newCookies = filteredExisting + convertedForThisRequest
        logger.trace { "New cookies" }
        logger.trace { newCookies.joinToString("; ") }
        return originalRequest.newBuilder()
            .header("Cookie", newCookies.joinToString("; ") { "${it.name}=${it.value}" })
            .build()
    }

    fun getWebViewUserAgent(): String {
        return try {
            if (!serverConfig.webviewEnabled) {
                throw CloudflareBypassException("Webview is disabled, enable it in server config")
            }

            initializeUndetectedChrome()
            SharedInterpreter().use { jep ->
                jep.exec("import undetected_chromedriver as uc")

                jep.exec("options = uc.ChromeOptions()")
//                jep.exec("options.add_argument('--headless')")
//                jep.exec("options.add_argument('--disable-gpu')")
                jep.exec("driver = uc.Chrome(options=options)")

                jep.exec("userAgent = driver.execute_script('return navigator.userAgent')")
                val userAgent = jep.getValue("userAgent", java.lang.String::class.java).toString()
                jep.exec("driver.quit()")

                userAgent
            }
        } catch (e: Exception) {
            // Webview might fail on headless environments like docker
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36"
        }
    }

    @Serializable
    data class PythonSeleniumCookie(
        val domain: String,
        val expiry: Long?,
        val httpOnly: Boolean,
        val name: String,
        val path: String,
        val sameSite: String,
        val secure: Boolean,
        val value: String
    )

    private val json by DI.global.instance<Json>()
    private fun getCookies(jep: SharedInterpreter): List<Cookie> {
        val challengeResolved = waitForChallengeResolve(jep)

        return if (challengeResolved) {
            jep.exec("import json")
            jep.exec("cookies = json.dumps(driver.get_cookies())")
            val cookiesJson = jep.getValue("cookies", java.lang.String::class.java).toString()
            val cookies = json.decodeFromString<List<PythonSeleniumCookie>>(cookiesJson)

            logger.debug {
                jep.exec("userAgent = driver.execute_script('return navigator.userAgent')")
                val userAgent = jep.getValue("userAgent", java.lang.String::class.java).toString()
                "Webview User-Agent is $userAgent"
            }

            // Convert Webview cookies to OkHttp cookies
            cookies.map {
                Cookie.Builder()
                    .domain(it.domain.removePrefix("."))
                    .expiresAt(it.expiry?.times(1000) ?: Long.MAX_VALUE)
                    .name(it.name)
                    .path(it.path)
                    .value(it.value).apply {
                        if (it.httpOnly) httpOnly()
                        if (it.secure) secure()
                    }.build()
            }
        } else {
            throw CloudflareBypassException("Cloudflare challenge failed to resolve")
        }
    }

    private fun waitForChallengeResolve(jep: SharedInterpreter): Boolean {
        // sometimes the user has to solve the captcha challenge manually and multiple times, potentially wait a long time
        val timeoutSeconds = 120
        repeat(timeoutSeconds) {
            TimeUnit.SECONDS.sleep(1)
            val success = try {
                jep.exec("r = driver.execute_script('return document.querySelector(\"#challenge-form\") == null')")
                jep.getValue("r", java.lang.Boolean::class.java).toString().toBoolean()
            } catch (e: Exception) {
                logger.debug(e) { "query Error" }
                false
            }
            if (success) return true
        }
        return false
    }
}
private class CloudflareBypassException(message: String?) : Exception(message)
