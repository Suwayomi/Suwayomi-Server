package suwayomi.tachidesk.manga.impl.update

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy

object UpdaterSocket : Websocket<UpdateStatus>() {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val updater: IUpdater by injectLazy()
    private var job: Job? = null

    override fun notifyClient(
        ctx: WsContext,
        value: UpdateStatus?,
    ) {
        ctx.send(value ?: updater.statusDeprecated.value)
    }

    override fun handleRequest(ctx: WsMessageContext) {
        when (ctx.message()) {
            "STATUS" -> notifyClient(ctx, updater.statusDeprecated.value)
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
        logger.info { ctx.sessionId() }
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

    fun start(): Job =
        updater.status
            .onEach {
                notifyAllClients(it)
            }.launchIn(scope)
}
