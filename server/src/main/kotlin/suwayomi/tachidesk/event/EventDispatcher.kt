package suwayomi.tachidesk.event

import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

abstract class EventDispatcher<T : EventEntity> {
    private val clients = ConcurrentHashMap<String, WsContext>()
    private val eventQueue = CopyOnWriteArrayList<Event<T>>()

    fun addClient(ctx: WsContext) {
        clients[ctx.sessionId] = ctx
    }

    fun removeClient(ctx: WsContext) {
        clients.remove(key = ctx.sessionId)
    }

    abstract fun notifyClient(ctx: WsContext, event: Event<T>)

    abstract fun handleRequest(ctx: WsMessageContext)

    fun notifyAllClients(event: Event<T>) {
        clients.forEach {
            notifyClient(ctx = it.value, event = event)
        }
    }

    fun enqueue(event: Event<T>) {
        eventQueue += event
    }

    // to be consumed in the client based on
    // the event type in a notifications screen
    fun queue() = eventQueue

    fun dequeue(event: Event<T>) {
        eventQueue.removeIf { it.id == event.id }
    }
}
