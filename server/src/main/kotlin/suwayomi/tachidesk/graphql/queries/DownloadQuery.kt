package suwayomi.tachidesk.graphql.queries

import kotlinx.coroutines.flow.first
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class DownloadQuery {
    fun downloadStatus(): CompletableFuture<DownloadStatus> {
        return future {
            DownloadStatus(DownloadManager.status.first())
        }
    }
}
