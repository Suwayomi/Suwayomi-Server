package suwayomi.tachidesk.manga.impl.update

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import java.util.concurrent.ConcurrentHashMap

abstract class Websocket<T> {
    private val logger = KotlinLogging.logger {}
    protected val clients = ConcurrentHashMap<String, WsContext>()

    open fun addClient(ctx: WsContext) {
        clients[ctx.sessionId()] = ctx
        notifyClient(ctx, null)
    }

    open fun removeClient(ctx: WsContext) {
        clients.remove(ctx.sessionId())
    }

    open fun notifyAllClients(value: T) {
        clients.values.forEach { ctx ->
            // a half-open/broken client must not kill the collecting job and silently stop the
            // broadcast for everyone else — drop it instead
            try {
                notifyClient(ctx, value)
            } catch (e: Exception) {
                logger.debug(e) { "Failed to notify websocket client ${ctx.sessionId()}, removing it" }
                clients.remove(ctx.sessionId(), ctx)
            }
        }
    }

    abstract fun notifyClient(
        ctx: WsContext,
        value: T?,
    )

    abstract fun handleRequest(ctx: WsMessageContext)
}
