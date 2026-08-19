package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class DownloadQuery {
    @RequireAuth
    fun downloadStatus(): CompletableFuture<DownloadStatus> =
        future {
            DownloadStatus(DownloadManager.getStatus())
        }
}
