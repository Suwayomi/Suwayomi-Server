package suwayomi.tachidesk.graphql.queries

import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.graphql.types.UpdateStatusType
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.JobStatus

class UpdateQuery {
    private val updater by DI.global.instance<IUpdater>()

    fun updateStatus(): UpdateStatus {
        val status = updater.status.value
        return UpdateStatus(
            isRunning = status.running,
            pendingJobs = UpdateStatusType(status.statusMap[JobStatus.PENDING]?.map { it.id }.orEmpty()),
            runningJobs = UpdateStatusType(status.statusMap[JobStatus.RUNNING]?.map { it.id }.orEmpty()),
            completeJobs = UpdateStatusType(status.statusMap[JobStatus.COMPLETE]?.map { it.id }.orEmpty()),
            failedJobs = UpdateStatusType(status.statusMap[JobStatus.FAILED]?.map { it.id }.orEmpty())
        )
    }

    data class LastUpdateTimestampPayload(val timestamp: Long)

    fun lastUpdateTimestamp(): LastUpdateTimestampPayload {
        return LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
    }
}
