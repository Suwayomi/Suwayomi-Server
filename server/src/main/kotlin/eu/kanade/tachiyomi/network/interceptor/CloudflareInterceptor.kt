package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypasser.resolveWithWebView
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
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
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

object CloudflareBypasser {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()

    fun resolveWithWebView(originalRequest: Request): Request {
        val url = originalRequest.url.toString()

        logger.debug { "resolveWithWebView($url)" }

        val cookies = PythonInterpreter.create().use { py ->
            try {
                py.exec("import undetected_chromedriver as uc")

                py.exec("options = uc.ChromeOptions()")

                if (serverConfig.socksProxyEnabled) {
                    val proxy = "socks5://${serverConfig.socksProxyHost}:${serverConfig.socksProxyPort}"
                    py.exec("options.add_argument('--proxy-server=$proxy')")
                }
                py.exec("driver = uc.Chrome(options=options)")
                py.exec("driver = uc.Chrome(options=options, driver_executable_path='${py.chromedriverPath.replace("\\","\\\\")}')")

                // TODO: handle custom userAgent
//                val userAgent = originalRequest.header("User-Agent")

//                if (userAgent != null) {
//                    browser.newContext(Browser.NewContextOptions().setUserAgent(userAgent)).use { browserContext ->
//                        browserContext.newPage().use { getCookies(it, url) }
//                    }
//                } else {
//                    browser.newPage().use { getCookies(it, url) }
//                }
                py.exec("driver.get('$url')")

                getCookies(py)
            } finally {
                py.exec("driver.quit()")
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

            PythonInterpreter.create().use { py ->
                py.exec("import undetected_chromedriver as uc")

                py.exec("options = uc.ChromeOptions()")
                py.exec("options.add_argument('--headless')")
                py.exec("options.add_argument('--disable-gpu')")
                py.exec("driver = uc.Chrome(options=options)")
//                py.exec("driver = uc.Chrome(options=options, driver_executable_path='${py.chromedriverPath.replace("\\","\\\\")}')")

                py.exec("userAgent = driver.execute_script('return navigator.userAgent')")
                val userAgent = py.getValue("userAgent")
                py.exec("driver.quit()")

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
    private fun getCookies(py: PythonInterpreter): List<Cookie> {
        val challengeResolved = waitForChallengeResolve(py)

        return if (challengeResolved) {
            py.exec("import json")
            py.exec("cookies = json.dumps(driver.get_cookies())")
            val cookiesJson = py.getValue("cookies")
            val cookies = json.decodeFromString<List<PythonSeleniumCookie>>(cookiesJson)

            logger.debug {
                py.exec("userAgent = driver.execute_script('return navigator.userAgent')")
                val userAgent = py.getValue("userAgent")
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

    private fun waitForChallengeResolve(py: PythonInterpreter): Boolean {
        // sometimes the user has to solve the captcha challenge manually and multiple times, potentially wait a long time
        val timeoutSeconds = 120
        repeat(timeoutSeconds) {
            TimeUnit.SECONDS.sleep(1)
            val success = try {
                py.exec("r = driver.execute_script('return document.querySelector(\"#challenge-form\") == null')")
                py.getValue("r").lowercase().toBoolean()
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

class PythonInterpreter
private constructor(private val process: Process, val chromedriverPath: String) : Closeable {
    private val logger = KotlinLogging.logger {}

    private val stdin = process.outputStream
    private val stdout = process.inputStream
    private val stderr = process.errorStream

    private val stdinWriter = PrintWriter(stdin)
    private val stdoutReader = BufferedReader(InputStreamReader(stdout))
    private val stderrReader = BufferedReader(InputStreamReader(stderr))

    private fun rawExec(command: String) {
        stdinWriter.println(command)
        stdinWriter.flush()
    }

    val BUFF_SIZE = 102400
    fun exec(command: String) {
        logger.debug { "Python Command: $command" }
        rawExec(command)
        makeSureExecDone()
    }

    private val commandOutputs = mutableListOf<String>()

    fun makeSureExecDone() {
        val makeSureString = "PYTHON_IS_READY"

        rawExec("print('$makeSureString')")
        var line: String?
        do {
            line = stdoutReader.readLine()
            if (line != makeSureString) {
                commandOutputs.add(line)
            }
        } while (line != makeSureString)

        val pyError = buildString {
            while (stderrReader.ready())
                append(stderr.read().toChar())
        }
        if (pyError.isNotEmpty()) {
            println("Python STDERR: $pyError")
        }
    }

    fun getValue(variableName: String): String {
        exec("print($variableName)")
        return commandOutputs.last()
    }

    private fun flushStdoutReader() {
        var line: String?
        while (stdoutReader.ready()) {
            val line = stdoutReader.readLine()
        }
    }

    fun destroy() {
        stdinWriter.close()
        stdoutReader.close()
        stderr.close()
        process.destroy()
    }
    override fun close() {
        destroy()
    }
    companion object {
        fun create(pythonPath: String, workingDir: String, pythonStartupFile: String, chromedriverPath: String): PythonInterpreter {
            val processBuilder = ProcessBuilder()
                .command(pythonPath, "-i", "-q")
            processBuilder.directory(File(workingDir))

            val environment = processBuilder.environment()
            environment["PYTHONSTARTUP"] = pythonStartupFile

            val process = processBuilder.start()

            return PythonInterpreter(process, chromedriverPath)
        }

        fun create(): PythonInterpreter {
            val uc = File(serverConfig.undetectedChromePath).absolutePath

            val (pythonPath, chromedriverPath) = if (System.getProperty("os.name").startsWith("Windows")) {
                arrayOf(
                    "$uc\\venv\\Scripts\\python.exe",
                    "$uc\\chromedriver.exe"
                )
            } else {
                arrayOf(
                    "$uc/venv/bin/python",
                    "$uc/chromedriver"
                )
            }

            return create(
                pythonPath,
                uc,
                "$uc/console.py",
                chromedriverPath
            )
        }
    }
}
