package suwayomi.tachidesk.graphql.queries

import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater

class UpdateQuery {
    private val updater by DI.global.instance<IUpdater>()

    fun updateStatus(): UpdateStatus {
        return UpdateStatus(updater.status.value)
    }

    data class LastUpdateTimestampPayload(val timestamp: Long)

    fun lastUpdateTimestamp(): LastUpdateTimestampPayload {
        return LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
    }
}
