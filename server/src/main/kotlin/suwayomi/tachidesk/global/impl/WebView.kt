package suwayomi.tachidesk.global.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.eclipse.jetty.websocket.core.CloseStatus
import suwayomi.tachidesk.manga.impl.update.Websocket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_UNACKNOWLEDGED_FRAMES = 2

object WebView : Websocket<String>() {
    private val logger = KotlinLogging.logger {}

    @Volatile
    private var driver: KcefWebView? = null
    private val unacknowledgedFrames = AtomicInteger()

    // The client confirms every frame it receives. Waiting for that, instead of only for the socket write,
    // keeps frames from piling up in network buffers on slow connections.
    val canSendFrame: Boolean
        get() = unacknowledgedFrames.get() < MAX_UNACKNOWLEDGED_FRAMES

    override fun addClient(ctx: WsContext) {
        if (clients.isNotEmpty()) {
            // TODO: allow multiple concurrent accesses?
            clients.forEach { it.value.closeSession(CloseStatus(1001, "Other client connected")) }
            clients.clear()
        }
        unacknowledgedFrames.set(0)
        if (driver == null) {
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

    // Frames are sent from a background thread and events from KCEF's threads. Sends are asynchronous,
    // the lock only keeps them from being queued concurrently.
    override fun notifyClient(
        ctx: WsContext,
        value: String?,
    ) {
        if (value != null) {
            synchronized(ctx) { ctx.send(value) }
        }
    }

    fun sendFrame(
        jpeg: ByteArray,
        source: KcefWebView,
    ) {
        // a frame encoded while its WebView was being destroyed must not reach the next client
        if (driver !== source) return
        clients.values.forEach { ctx ->
            unacknowledgedFrames.incrementAndGet()
            try {
                synchronized(ctx) { ctx.send(ByteBuffer.wrap(jpeg)) }
            } catch (e: Exception) {
                unacknowledgedFrames.decrementAndGet()
                throw e
            }
        }
    }

    @Serializable
    sealed class TypeObject

    @Serializable
    @SerialName("loadUrl")
    private data class LoadUrlMessage(
        val url: String,
        val width: Int,
        val height: Int,
    ) : TypeObject()

    @Serializable
    @SerialName("resize")
    private data class ResizeMessage(
        val width: Int,
        val height: Int,
    ) : TypeObject()

    @Serializable
    @SerialName("event")
    data class JsEventMessage(
        val eventType: String,
        val clickX: Float,
        val clickY: Float,
        val button: Int? = null,
        val ctrlKey: Boolean? = null,
        val shiftKey: Boolean? = null,
        val altKey: Boolean? = null,
        val metaKey: Boolean? = null,
        val key: String? = null,
        val code: String? = null,
        val clientX: Float? = null,
        val clientY: Float? = null,
        val deltaY: Float? = null,
    ) : TypeObject()

    @Serializable
    @SerialName("paste")
    data class JsPasteMessage(
        val data: String,
    ) : TypeObject()

    @Serializable
    @SerialName("copy")
    class JsCopyMessage : TypeObject()

    @Serializable
    @SerialName("ping")
    class JsPingMessage : TypeObject()

    @Serializable
    @SerialName("frameReceived")
    class JsFrameReceivedMessage : TypeObject()

    override fun handleRequest(ctx: WsMessageContext) {
        val dr = driver ?: return
        try {
            val event = Json.decodeFromString<TypeObject>(ctx.message())
            when (event) {
                is LoadUrlMessage -> {
                    val url = event.url
                    dr.loadUrl(url)
                    dr.resize(event.width, event.height)
                    logger.debug { "Loading URL $url" }
                }

                is ResizeMessage -> {
                    dr.resize(event.width, event.height)
                }

                is JsEventMessage -> {
                    dr.event(event)
                }

                is JsPasteMessage -> {
                    dr.paste(event.data)
                }

                is JsCopyMessage -> {
                    dr.copy()
                }

                is JsPingMessage -> {
                    notifyAllClients("{\"type\":\"pong\"}")
                }

                is JsFrameReceivedMessage -> {
                    unacknowledgedFrames.updateAndGet { maxOf(0, it - 1) }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to deserialize client request: ${ctx.message()}" }
        }
    }
}
