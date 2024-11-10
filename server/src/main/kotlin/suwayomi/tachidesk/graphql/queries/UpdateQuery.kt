package suwayomi.tachidesk.graphql.queries

import kotlinx.coroutines.flow.first
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.JavalinSetup.future
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture

class UpdateQuery {
    private val updater: IUpdater by injectLazy()

    fun updateStatus(): CompletableFuture<UpdateStatus> = future { UpdateStatus(updater.status.first()) }

    data class LastUpdateTimestampPayload(
        val timestamp: Long,
    )

    fun lastUpdateTimestamp(): LastUpdateTimestampPayload = LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
}
