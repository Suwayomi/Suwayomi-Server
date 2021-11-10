package suwayomi.tachidesk.manga.impl.update

import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance

object UpdaterSocket : Websocket() {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val updater by DI.global.instance<IUpdater>()
    private var job: Job? = null

    override fun notifyClient(ctx: WsContext) {
        ctx.send(updater.getStatus().value.toJson())
    }

    override fun handleRequest(ctx: WsMessageContext) {
        ctx.send(updater.getStatus().value.toJson())
    }

    override fun addClient(ctx: WsContext) {
        logger.info { ctx.sessionId }
        super.addClient(ctx)
        if (job == null) {
            job = start()
        }
    }

    override fun removeClient(ctx: WsContext) {
        super.removeClient(ctx)
        if (clients.isEmpty()) {
            job?.cancel()
            job = null
        }
    }

    fun start(): Job {
        return scope.launch {
            while (true) {
                updater.getStatus().collectLatest {
                    notifyAllClients()
                }
            }
        }
    }
}
