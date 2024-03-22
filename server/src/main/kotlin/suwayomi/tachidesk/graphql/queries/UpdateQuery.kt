package suwayomi.tachidesk.graphql.queries

import kotlinx.coroutines.flow.first
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class UpdateQuery {
    private val updater by DI.global.instance<IUpdater>()

    fun updateStatus(): CompletableFuture<UpdateStatus> {
        return future { UpdateStatus(updater.status.first()) }
    }

    data class LastUpdateTimestampPayload(val timestamp: Long)

    fun lastUpdateTimestamp(): LastUpdateTimestampPayload {
        return LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
    }
}
