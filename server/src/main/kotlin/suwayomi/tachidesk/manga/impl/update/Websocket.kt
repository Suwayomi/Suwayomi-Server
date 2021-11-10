package suwayomi.tachidesk.manga.impl.update

import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import java.util.concurrent.ConcurrentHashMap

abstract class Websocket {
    protected val clients = ConcurrentHashMap<String, WsContext>()
    open fun addClient(ctx: WsContext) {
        clients[ctx.sessionId] = ctx
        notifyClient(ctx)
    }
    open fun removeClient(ctx: WsContext) {
        clients.remove(ctx.sessionId)
    }
    open fun notifyAllClients() {
        clients.values.forEach { notifyClient(it) }
    }
    abstract fun notifyClient(ctx: WsContext)
    abstract fun handleRequest(ctx: WsMessageContext)
}
