package suwayomi.tachidesk.graphql.queries

import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.KoSyncStatusPayload
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import java.util.concurrent.CompletableFuture

class KoreaderSyncQuery {
    fun koSyncStatus(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<KoSyncStatusPayload> =
        future {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
            KoreaderSyncService.getStatus()
        }
}
