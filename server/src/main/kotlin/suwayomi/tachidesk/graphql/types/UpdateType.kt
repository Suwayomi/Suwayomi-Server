package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.manga.impl.update.JobStatus
import suwayomi.tachidesk.manga.impl.update.UpdateStatus
import java.util.concurrent.CompletableFuture

data class UpdateStatus(
    val isRunning: Boolean,
    val pendingJobs: UpdateStatusType,
    val runningJobs: UpdateStatusType,
    val completeJobs: UpdateStatusType,
    val failedJobs: UpdateStatusType
) {
    constructor(status: UpdateStatus) : this(
        isRunning = status.running,
        pendingJobs = UpdateStatusType(status.statusMap[JobStatus.PENDING]?.map { it.id }.orEmpty()),
        runningJobs = UpdateStatusType(status.statusMap[JobStatus.RUNNING]?.map { it.id }.orEmpty()),
        completeJobs = UpdateStatusType(status.statusMap[JobStatus.COMPLETE]?.map { it.id }.orEmpty()),
        failedJobs = UpdateStatusType(status.statusMap[JobStatus.FAILED]?.map { it.id }.orEmpty())
    )
}

data class UpdateStatusType(
    @get:GraphQLIgnore
    val mangaIds: List<Int>
) {
    fun mangas(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaNodeList> {
        return dataFetchingEnvironment.getValueFromDataLoader<List<Int>, MangaNodeList>("MangaForIdsDataLoader", mangaIds)
    }
}
