package suwayomi.tachidesk.manga.impl.update

import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance

object UpdaterSocket : Websocket<UpdateStatus>() {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val updater by DI.global.instance<IUpdater>()
    private var job: Job? = null

    override fun notifyClient(
        ctx: WsContext,
        value: UpdateStatus?,
    ) {
        ctx.send(value ?: updater.status.value)
    }

    override fun handleRequest(ctx: WsMessageContext) {
        when (ctx.message()) {
            "STATUS" -> notifyClient(ctx, updater.status.value)
            else ->
                ctx.send(
                    """
                        |Invalid command.
                        |Supported commands are:
                        |    - STATUS
                        |       sends the current update status
                        |
                    """.trimMargin(),
                )
        }
    }

    override fun addClient(ctx: WsContext) {
        logger.info { ctx.sessionId }
        super.addClient(ctx)
        if (job?.isActive != true) {
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
        return updater.status
            .onEach {
                notifyAllClients(it)
            }
            .launchIn(scope)
    }
}
