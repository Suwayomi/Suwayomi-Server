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
    @Serializable
    @SerialName("event")
    private data class JsEventMessage(
            val eventType: String,
            val elementPath: String,
            val inputValueAfter: String? = null,
            val bubbles: Boolean? = null,
            val cancelable: Boolean? = null,
            val composed: Boolean? = null,
            val detail: Int? = null,
            val button: Int? = null,
            val buttons: Int? = null,
            val ctrlKey: Boolean? = null,
            val shiftKey: Boolean? = null,
            val altKey: Boolean? = null,
            val metaKey: Boolean? = null,
            val key: String? = null,
            val code: String? = null,
            val charCode: Int? = null,
            val keyCode: Int? = null,
            val which: Int? = null,
            val clientX: Int? = null,
            val clientY: Int? = null,
            val movementX: Int? = null,
            val movementY: Int? = null,
            val offsetX: Int? = null,
            val offsetY: Int? = null,
            val pageX: Int? = null,
            val pageY: Int? = null,
            val screenX: Int? = null,
            val screenY: Int? = null,
    ) : TypeObject() {
        public fun toJsConstructor(): String {
            return """
                   {
                       view: window,
                       inputValueAfter: ${Json.encodeToString(inputValueAfter)},
                       bubbles: ${Json.encodeToString(bubbles)},
                       cancelable: ${Json.encodeToString(cancelable)},
                       composed: ${Json.encodeToString(composed)},
                       detail: ${Json.encodeToString(detail)},
                       button: ${Json.encodeToString(button)},
                       buttons: ${Json.encodeToString(buttons)},
                       ctrlKey: ${Json.encodeToString(ctrlKey)},
                       shiftKey: ${Json.encodeToString(shiftKey)},
                       altKey: ${Json.encodeToString(altKey)},
                       metaKey: ${Json.encodeToString(metaKey)},
                       key: ${Json.encodeToString(key)},
                       code: ${Json.encodeToString(code)},
                       charCode: ${Json.encodeToString(charCode)},
                       keyCode: ${Json.encodeToString(keyCode)},
                       which: ${Json.encodeToString(which)},
                       clientX: ${Json.encodeToString(clientX)},
                       clientY: ${Json.encodeToString(clientY)},
                       movementX: ${Json.encodeToString(movementX)},
                       movementY: ${Json.encodeToString(movementY)},
                       offsetX: ${Json.encodeToString(offsetX)},
                       offsetY: ${Json.encodeToString(offsetY)},
                       pageX: ${Json.encodeToString(pageX)},
                       pageY: ${Json.encodeToString(pageY)},
                       screenX: ${Json.encodeToString(screenX)},
                       screenY: ${Json.encodeToString(screenY)},
                   }
                   """
        }
    }

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
                is JsEventMessage -> {
                    val path = event.elementPath
                    val type = event.eventType
                    dr.event(path, type, event.toJsConstructor())
                    logger.info { "$type on $path" }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to deserialize client request: ${ctx.message()}" }
        }
    }
}
