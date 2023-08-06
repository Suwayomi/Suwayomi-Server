package suwayomi.tachidesk.global.impl

import eu.kanade.tachiyomi.source.online.HttpSource
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import org.openqa.selenium.Dimension
import org.openqa.selenium.Keys
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
import java.util.concurrent.Executors

object WebView : Websocket<String>() {
    val json: Json by injectLazy()

    private var driver: SeleniumScreenshotServer? = null

    override fun addClient(ctx: WsContext) {
        if (clients.isNotEmpty()) {
            clients.forEach {
                it.value.closeSession(CloseStatus(1001, "Other client connected"))
            }
            clients.clear()
        } else {
            driver = SeleniumScreenshotServer()
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

class SeleniumScreenshotServer : Closeable {
    companion object {
        const val width = 1200
        const val height = 800
    }

    private val driver: WebDriver = run {
        val options = ChromeOptions()
        options.addArguments("--headless")
        ChromeDriver(options)
    }
    init {
        driver.manage().window().size = Dimension(width, height)
        driver.get("https://google.com")
    }
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
    }

    fun handleEvent(jsonObject: JsonObject) {
        when (jsonObject["type"]!!.jsonPrimitive.content) {
            "click" -> {
                val x = jsonObject["x"]!!.jsonPrimitive.double.toInt()
                val y = jsonObject["y"]!!.jsonPrimitive.double.toInt()
                if (x in 0..width && y in 0..height) {
                    Actions(driver).moveToLocation(x, y).click().perform()
                }
            }
            "keypress" -> {
                val key = jsonObject["key"]!!.jsonPrimitive.content
                val keys: CharSequence = when (key) {
                    "Backspace" -> Keys.BACK_SPACE
                    "Tab" -> Keys.TAB
                    "Enter" -> Keys.ENTER
                    else -> key
                }
                Actions(driver).keyDown(keys).keyUp(keys).perform()
            }
            "back" -> {
                driver.navigate().back()
            }
            "forward" -> {
                driver.navigate().forward()
            }
            "loadsource" -> {
                val sourceId = jsonObject["source"]!!.jsonPrimitive.longOrNull ?: return
                val source = getCatalogueSourceOrNull(sourceId) as? HttpSource ?: return
                driver.get(source.baseUrl)
            }
            "loadmanga" -> {
                val mangaId = jsonObject["manga"]!!.jsonPrimitive.intOrNull ?: return
                val manga = transaction { MangaTable.select { MangaTable.id eq mangaId }.firstOrNull() } ?: return
                driver.get(manga[MangaTable.realUrl] ?: return)
            }
            "loadchapter" -> {
                val chapterId = jsonObject["chapter"]!!.jsonPrimitive.intOrNull ?: return
                val chapter = transaction { ChapterTable.select { ChapterTable.id eq chapterId }.firstOrNull() } ?: return
                driver.get(chapter[ChapterTable.realUrl] ?: return)
            }
        }
    }

    override fun close() {
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
}
