package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.KoSyncStatusPayload
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class KoreaderSyncQuery {
    @RequireAuth
    fun koSyncStatus(): CompletableFuture<KoSyncStatusPayload> =
        future {
            KoreaderSyncService.getStatus()
        }
}
