package suwayomi.tachidesk.global.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.eclipse.jetty.websocket.core.CloseStatus
import suwayomi.tachidesk.manga.impl.update.Websocket

object WebView : Websocket<String>() {
    private val logger = KotlinLogging.logger {}
    private var driver: KcefWebView? = null

    override fun addClient(ctx: WsContext) {
        if (clients.isNotEmpty()) {
            // TODO: allow multiple concurrent accesses?
            clients.forEach { it.value.closeSession(CloseStatus(1001, "Other client connected")) }
            clients.clear()
        }
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

    override fun notifyClient(
        ctx: WsContext,
        value: String?,
    ) {
        if (value != null) {
            ctx.send(value)
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

    // Server-to-client only event; a TypeObject subclass so the encoded JSON
    // carries the "type" discriminator the Webview.kte switch relies on.
    @Serializable
    @SerialName("error")
    private data class ErrorEvent(
        val message: String,
    ) : TypeObject()

    override fun handleRequest(ctx: WsMessageContext) {
        val dr = driver ?: return
        try {
            val event = Json.decodeFromString<TypeObject>(ctx.message())
            when (event) {
                is LoadUrlMessage -> {
                    val url = event.url
                    if (WebViewUrlPolicy.isAllowed(url)) {
                        dr.loadUrl(url)
                        dr.resize(event.width, event.height)
                        logger.debug { "Loading URL $url" }
                    } else {
                        logger.warn { "Refusing to load disallowed URL: $url" }
                        notifyClient(
                            ctx,
                            Json.encodeToString<TypeObject>(
                                ErrorEvent("Refused to load URL, only http/https URLs are allowed: $url"),
                            ),
                        )
                    }
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

                is ErrorEvent -> {
                    // Outgoing-only event; ignore if a client sends it anyway
                }
            }
        } catch (e: SerializationException) {
            logger.warn(e) { "Failed to deserialize client request: ${ctx.message()}" }
        } catch (e: IllegalArgumentException) {
            logger.warn(e) { "URL rejected by policy" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to handle client request: ${ctx.message()}" }
        }
    }
}
