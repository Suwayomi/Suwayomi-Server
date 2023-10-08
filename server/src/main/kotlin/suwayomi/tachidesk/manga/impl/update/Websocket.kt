package suwayomi.tachidesk.manga.impl.update

import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import java.util.concurrent.ConcurrentHashMap

abstract class Websocket<T> {
    protected val clients = ConcurrentHashMap<String, WsContext>()

    open fun addClient(ctx: WsContext) {
        clients[ctx.sessionId] = ctx
        notifyClient(ctx, null)
    }

    open fun removeClient(ctx: WsContext) {
        clients.remove(ctx.sessionId)
    }

    open fun notifyAllClients(value: T) {
        clients.values.forEach { notifyClient(it, value) }
    }

    abstract fun notifyClient(
        ctx: WsContext,
        value: T?,
    )

    abstract fun handleRequest(ctx: WsMessageContext)
}
