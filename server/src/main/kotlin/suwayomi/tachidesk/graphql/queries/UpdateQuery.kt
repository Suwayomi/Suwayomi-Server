package suwayomi.tachidesk.graphql.queries

import graphql.schema.DataFetchingEnvironment
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.requireUser

class UpdateQuery {
    private val updater by DI.global.instance<IUpdater>()

    fun updateStatus(dataFetchingEnvironment: DataFetchingEnvironment): UpdateStatus {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return UpdateStatus(updater.status.value)
    }

    data class LastUpdateTimestampPayload(val timestamp: Long)

    fun lastUpdateTimestamp(dataFetchingEnvironment: DataFetchingEnvironment): LastUpdateTimestampPayload {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
    }
}
