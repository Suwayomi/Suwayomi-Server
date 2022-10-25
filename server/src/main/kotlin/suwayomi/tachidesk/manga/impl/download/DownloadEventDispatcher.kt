package suwayomi.tachidesk.manga.impl.download

import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import suwayomi.tachidesk.event.Event
import suwayomi.tachidesk.event.EventDispatcher
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus

class DownloadEventDispatcher : EventDispatcher<DownloadStatus>() {
    override fun notifyClient(ctx: WsContext, event: Event<DownloadStatus>) {
        ctx.send(
            event.entity
        )
    }

    override fun handleRequest(ctx: WsMessageContext) {
        when (ctx.message()) {
            "STATUS" -> DownloadManager.notifyClient(ctx)
            else -> ctx.send(
                """
                        |Invalid command.
                        |Supported commands are:
                        |    - STATUS
                        |       sends the current download status
                        |
                """.trimMargin()
            )
        }
    }
}
