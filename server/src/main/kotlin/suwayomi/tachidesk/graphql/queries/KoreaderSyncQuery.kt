package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.KoSyncStatusPayload
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class KoreaderSyncQuery {
    @RequireAuth
    fun koSyncStatus(
        @GraphQLIgnore
        userId: Int,
    ): CompletableFuture<KoSyncStatusPayload> =
        future {
            KoreaderSyncService.getStatus(userId)
        }
}
