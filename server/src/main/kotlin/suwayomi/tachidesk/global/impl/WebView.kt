package suwayomi.tachidesk.global.impl

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.ScreenSize
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CFClearance
import eu.kanade.tachiyomi.source.online.HttpSource
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.eclipse.jetty.websocket.api.CloseStatus
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.openqa.selenium.Cookie
import org.openqa.selenium.Keys
import org.openqa.selenium.NoSuchSessionException
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.Actions
import suwayomi.tachidesk.manga.impl.update.Websocket
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrNull
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import uy.kohesive.injekt.injectLazy
import java.io.Closeable
import java.net.HttpCookie
import java.net.URI
import java.util.Date
import java.util.concurrent.Executors
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object WebView : Websocket<String>() {
    val json: Json by injectLazy()

    private var driver: ScreenShotWebViewEventListener? = null

    override fun addClient(ctx: WsContext) {
        if (clients.isNotEmpty()) {
            clients.forEach {
                it.value.closeSession(CloseStatus(1001, "Other client connected"))
            }
            clients.clear()
        } else {
            driver = PlaywrightScreenshotServer() // SeleniumScreenshotServer()
        }
        super.addClient(ctx)
    }

    override fun removeClient(ctx: WsContext) {
        super.removeClient(ctx)
        if (clients.isEmpty()) {
            driver?.close()
            driver = null
        }
    }

    override fun notifyClient(ctx: WsContext, value: String?) {
        if (value != null) {
            ctx.send(value)
        }
    }

    override fun handleRequest(ctx: WsMessageContext) {
        try {
            val event = json.decodeFromString<JsonObject>(ctx.message())
            driver?.handleEvent(event)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

interface ScreenShotWebViewEventListener : Closeable {
    fun handleEvent(jsonObject: JsonObject) {
        when (jsonObject["type"]!!.jsonPrimitive.content) {
            "click" -> {
                val x = jsonObject["x"]!!.jsonPrimitive.double.toInt()
                val y = jsonObject["y"]!!.jsonPrimitive.double.toInt()
                onClick(x, y)
            }
            "keypress" -> {
                val key = jsonObject["key"]!!.jsonPrimitive.content
                keyPress(key)
            }
            "back" -> {
                back()
            }
            "forward" -> {
                forward()
            }
            "loadsource" -> {
                val sourceId = jsonObject["source"]!!.jsonPrimitive.longOrNull ?: return
                val source = getCatalogueSourceOrNull(sourceId) as? HttpSource ?: return
                loadUrl(source.baseUrl)
            }
            "loadmanga" -> {
                val mangaId = jsonObject["manga"]!!.jsonPrimitive.intOrNull ?: return
                val manga = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull() } ?: return
                loadUrl(manga[MangaTable.realUrl] ?: return)
            }
            "loadchapter" -> {
                val chapterId = jsonObject["chapter"]!!.jsonPrimitive.intOrNull ?: return
                val chapter = transaction { ChapterTable.select { ChapterTable.id eq chapterId }.firstOrNull() } ?: return
                loadUrl(chapter[ChapterTable.realUrl] ?: return)
            }
        }
    }

    fun onClick(x: Int, y: Int)

    fun keyPress(key: String)

    fun back()

    fun forward()

    fun loadUrl(url: String)
}

class SeleniumScreenshotServer : Closeable, ScreenShotWebViewEventListener {
    companion object {
        const val width = 1200
        const val height = 800

        private val networkHelper: NetworkHelper by injectLazy()
    }

    private val driver: WebDriver = run {
        val options = ChromeOptions()
        options.addArguments(
            "--headless",
            "--no-default-browser-check",
            "--no-first-run",
            "--no-sandbox",
            "--test-type",
            "--window-size=$width,$height"
        )
        ChromeDriver(options)
    }
    init {
        driver.navigate().to("https://google.com")
    }
    private val job = SupervisorJob()
    private val executor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    init {
        GlobalScope.launch(executor) {
            while (isActive) {
                try {
                    // Capture screenshot
                    val screenshot =
                        (driver as TakesScreenshot).getScreenshotAs(OutputType.BASE64)
                    // Send image data over the socket
                    WebView.notifyAllClients(screenshot)
                    delay(1000) // Adjust interval as needed
                } catch (e: NoSuchSessionException) {
                    ensureActive()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        GlobalScope.launch(job) {
            while (true) {
                delay(5.seconds)
                flush()
            }
        }
    }

    override fun onClick(x: Int, y: Int) {
        if (x in 0..width && y in 0..height) {
            Actions(driver).moveToLocation(x, y).click().perform()
        }
    }

    override fun keyPress(key: String) {
        val keys: CharSequence = when (key) {
            "Backspace" -> Keys.BACK_SPACE
            "Tab" -> Keys.TAB
            "Enter" -> Keys.ENTER
            else -> key
        }
        Actions(driver).keyDown(keys).keyUp(keys).perform()
    }

    override fun back() {
        driver.navigate().back()
    }

    override fun forward() {
        driver.navigate().forward()
    }

    override fun loadUrl(url: String) {
        driver.navigate().to(url)
        addCookiesFor(driver.currentUrl)
        driver.navigate().refresh()
    }

    override fun close() {
        job.cancel()
        flush()
        executor.cancel()
        try {
            driver.quit()
        } catch (_: Exception) {
        }
        try {
            driver.close()
        } catch (_: Exception) {
        }
    }

    private fun flush() {
        driver.manage().cookies.forEach {
            networkHelper.cookieStore.add(
                URI("http://" + it.domain),
                HttpCookie(it.name, it.value).apply {
                    path = it.path
                    domain = it.domain
                    maxAge = if (it.expiry == null) {
                        -1
                    } else {
                        (it.expiry.time.milliseconds - System.currentTimeMillis().milliseconds).inWholeSeconds
                    }
                    isHttpOnly = it.isHttpOnly
                    secure = it.isSecure
                }
            )
        }
    }

    private fun addCookiesFor(uri: String) {
        networkHelper.cookieStore.get(URI(uri)).forEach {
            driver.manage().addCookie(
                Cookie(
                    it.name,
                    it.value,
                    it.domain,
                    it.path,
                    if (it.maxAge == -1L) {
                        null
                    } else {
                        Date(System.currentTimeMillis() + it.maxAge.seconds.inWholeMilliseconds)
                    },
                    it.secure,
                    it.isHttpOnly
                )
            )
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
class PlaywrightScreenshotServer : Closeable, ScreenShotWebViewEventListener {
    companion object {
        const val width = 1200
        const val height = 800

        private val networkHelper: NetworkHelper by injectLazy()
    }

    private val driver: Browser = run {
        Playwright.create().chromium().launch(
            BrowserType.LaunchOptions().apply {
                this.setHeadless(true)
            }
        )
    }
    val page = driver.newPage(
        Browser.NewPageOptions().apply {
            baseURL = "https://google.com"
            screenSize = ScreenSize(width, height)
        }
    )
    private val job = SupervisorJob()
    private val executor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    init {
        CFClearance.applyStealthInitScripts(page)
        GlobalScope.launch(executor) {
            val completableDeferred = CompletableDeferred<Unit>()
            page.onRequestFinished {
                completableDeferred.complete(Unit)
            }
            completableDeferred.await()
            while (isActive) {
                try {
                    // Capture screenshot
                    val screenshot = Base64.encode(page.screenshot())
                    // Send image data over the socket
                    WebView.notifyAllClients(screenshot)
                    delay(1000) // Adjust interval as needed
                } catch (e: NoSuchSessionException) {
                    ensureActive()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        GlobalScope.launch(job) {
            while (true) {
                delay(5.seconds)
                flush()
            }
        }
    }

    override fun onClick(x: Int, y: Int) {
        if (x in 0..width && y in 0..height) {
            page.mouse().click(x.toDouble(), y.toDouble())
        }
    }

    override fun keyPress(key: String) {
        page.keyboard().press(key)
    }

    override fun back() {
        page.goBack()
    }

    override fun forward() {
        page.goForward()
    }

    override fun loadUrl(url: String) {
        page.navigate(url)
        addCookiesFor(page.url())
        page.reload()
    }

    override fun close() {
        job.cancel()
        flush()
        executor.cancel()
        try {
            page.close()
        } catch (_: Exception) {
        }
        try {
            driver.close()
        } catch (_: Exception) {
        }
    }

    private fun flush() {
        page.context().cookies().forEach {
            networkHelper.cookieStore.add(
                URI("http://" + it.domain),
                HttpCookie(it.name, it.value).apply {
                    path = it.path
                    domain = it.domain
                    maxAge = if (it.expires == null) {
                        -1
                    } else {
                        it.expires.seconds.inWholeSeconds
                    }
                    isHttpOnly = it.httpOnly
                    secure = it.secure
                }
            )
        }
    }

    private fun addCookiesFor(uri: String) {
        page.context().addCookies(
            networkHelper.cookieStore.get(URI(uri)).map {
                com.microsoft.playwright.options.Cookie(
                    it.name,
                    it.value
                ).apply {
                    domain = it.domain
                    path = it.path
                    expires = if (it.maxAge == -1L) {
                        null
                    } else {
                        (System.currentTimeMillis().milliseconds + it.maxAge.seconds).inWholeSeconds.toDouble()
                    }
                    secure = it.secure
                    httpOnly = it.isHttpOnly
                }
            }
        )
    }
}
