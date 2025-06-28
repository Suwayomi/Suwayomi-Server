package suwayomi.tachidesk.global.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.eclipse.jetty.websocket.api.CloseStatus
import suwayomi.tachidesk.manga.impl.update.Websocket

object WebView : Websocket<String>() {
    private val logger = KotlinLogging.logger {}
    private var driver: KcefWebView? = null

    override fun addClient(ctx: WsContext) {
        if (clients.isNotEmpty()) {
            // TODO: allow multiple concurrent accesses?
            clients.forEach { it.value.closeSession(CloseStatus(1001, "Other client connected")) }
            clients.clear()
        } else {
            driver = KcefWebView()
        }
        super.addClient(ctx)
        ctx.enableAutomaticPings()
    }

    override fun removeClient(ctx: WsContext) {
        super.removeClient(ctx)
        if (clients.isEmpty()) {
            driver?.destroy()
            driver = null
        }
    }

    override fun notifyClient(ctx: WsContext, value: String?) {
        if (value != null) {
            ctx.send(value)
        }
    }

    @Serializable private sealed class TypeObject {}
    @Serializable
    @SerialName("loadUrl")
    private data class LoadUrlMessage(val url: String) : TypeObject()

    override fun handleRequest(ctx: WsMessageContext) {
        val dr = driver ?: return
        try {
            val event = Json.decodeFromString<TypeObject>(ctx.message())
            when (event) {
                is LoadUrlMessage -> {
                    val url = event.url
                    dr.loadUrl(url)
                    logger.info { "Loading URL $url" }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to deserialize client request: ${ctx.message()}" }
        }
    }
}
