package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.manga.impl.update.CategoryUpdateStatus
import suwayomi.tachidesk.manga.impl.update.JobStatus
import suwayomi.tachidesk.manga.impl.update.UpdateStatus
import java.util.concurrent.CompletableFuture

private val jobStatusToMangaIdsToCacheClearedStatus = mutableMapOf<JobStatus, MutableMap<Int, Boolean>>()

class UpdateStatus(
    val isRunning: Boolean,
    val skippedCategories: UpdateStatusCategoryType,
    val updatingCategories: UpdateStatusCategoryType,
    val pendingJobs: UpdateStatusType,
    val runningJobs: UpdateStatusType,
    val completeJobs: UpdateStatusType,
    val failedJobs: UpdateStatusType,
    val skippedJobs: UpdateStatusType,
) {
    constructor(status: UpdateStatus) : this(
        isRunning = status.running,
        skippedCategories = UpdateStatusCategoryType(status.categoryStatusMap[CategoryUpdateStatus.SKIPPED]?.map { it.id }.orEmpty()),
        updatingCategories = UpdateStatusCategoryType(status.categoryStatusMap[CategoryUpdateStatus.UPDATING]?.map { it.id }.orEmpty()),
        pendingJobs = UpdateStatusType(status.mangaStatusMap[JobStatus.PENDING]?.map { it.id }.orEmpty()),
        runningJobs = UpdateStatusType(status.mangaStatusMap[JobStatus.RUNNING]?.map { it.id }.orEmpty()),
        completeJobs =
            UpdateStatusType(
                status.mangaStatusMap[JobStatus.COMPLETE]?.map {
                    it.id
                }.orEmpty(),
                JobStatus.COMPLETE,
                status.running,
                true,
            ),
        failedJobs =
            UpdateStatusType(
                status.mangaStatusMap[JobStatus.FAILED]?.map { it.id }.orEmpty(),
                JobStatus.FAILED,
                status.running,
                true,
            ),
        skippedJobs = UpdateStatusType(status.mangaStatusMap[JobStatus.SKIPPED]?.map { it.id }.orEmpty()),
    )
}

class UpdateStatusCategoryType(
    @get:GraphQLIgnore
    val categoryIds: List<Int>,
) {
    fun categories(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<CategoryNodeList> {
        return dataFetchingEnvironment.getValueFromDataLoader("CategoryForIdsDataLoader", categoryIds)
    }
}

class UpdateStatusType(
    @get:GraphQLIgnore
    val mangaIds: List<Int>,
    private val jobStatus: JobStatus? = null,
    private val isRunning: Boolean = false,
    private val clearCache: Boolean = false,
) {
    fun mangas(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaNodeList> {
        val resetClearedMangaIds = !isRunning && clearCache && jobStatus != null
        if (resetClearedMangaIds) {
            jobStatusToMangaIdsToCacheClearedStatus[jobStatus]?.clear()
        }

        if (isRunning && clearCache && jobStatus != null) {
            val cacheClearedForMangaIds =
                jobStatusToMangaIdsToCacheClearedStatus.getOrPut(
                    jobStatus,
                ) { emptyMap<Int, Boolean>().toMutableMap() }

            mangaIds.forEach {
                if (cacheClearedForMangaIds[it] == true) {
                    return@forEach
                }

                MangaType.clearCacheFor(it, dataFetchingEnvironment)

                cacheClearedForMangaIds[it] = true
            }
        }

        return dataFetchingEnvironment.getValueFromDataLoader<List<Int>, MangaNodeList>("MangaForIdsDataLoader", mangaIds)
    }
}
