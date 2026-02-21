package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.manga.impl.update.CategoryUpdateJob
import suwayomi.tachidesk.manga.impl.update.CategoryUpdateStatus
import suwayomi.tachidesk.manga.impl.update.JobStatus
import suwayomi.tachidesk.manga.impl.update.UpdateJob
import suwayomi.tachidesk.manga.impl.update.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.UpdateUpdates
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
                status.mangaStatusMap[JobStatus.COMPLETE]
                    ?.map {
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

        return dataFetchingEnvironment.getValueFromDataLoader<List<Int>, MangaNodeList>(
            "MangaForIdsDataLoader",
            mangaIds,
        )
    }
}

class UpdateStatusCategoryType(
    @get:GraphQLIgnore
    val categoryIds: List<Int>,
) {
    fun categories(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<CategoryNodeList> =
        dataFetchingEnvironment.getValueFromDataLoader("CategoryForIdsDataLoader", categoryIds)
}

class LibraryUpdateStatus(
    val categoryUpdates: List<CategoryUpdateType>,
    val mangaUpdates: List<MangaUpdateType>,
    val jobsInfo: UpdaterJobsInfoType,
) {
    constructor(updates: UpdateUpdates) : this(
        categoryUpdates = updates.categoryUpdates.map(::CategoryUpdateType),
        mangaUpdates = updates.mangaUpdates.map(::MangaUpdateType),
        jobsInfo =
            UpdaterJobsInfoType(
                isRunning = updates.isRunning,
                totalJobs = updates.totalJobs,
                finishedJobs = updates.finishedJobs,
                skippedCategoriesCount = updates.skippedCategoriesCount,
                skippedMangasCount = updates.skippedMangasCount,
            ),
    )
}

enum class MangaJobStatus {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED,
    SKIPPED,
}

enum class CategoryJobStatus {
    UPDATING,
    SKIPPED,
}

class MangaUpdateType(
    @get:GraphQLIgnore
    val manga: MangaType,
    val status: MangaJobStatus,
) {
    constructor(job: UpdateJob) : this(
        MangaType(job.manga),
        when (job.status) {
            JobStatus.PENDING -> MangaJobStatus.PENDING
            JobStatus.RUNNING -> MangaJobStatus.RUNNING
            JobStatus.COMPLETE -> MangaJobStatus.COMPLETE
            JobStatus.FAILED -> MangaJobStatus.FAILED
            JobStatus.SKIPPED -> MangaJobStatus.SKIPPED
        },
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaType> {
        // Clearing the data loader cache here everytime should be fine, because a manga gets sent only once for each status
        val clearCache = status === MangaJobStatus.COMPLETE || status === MangaJobStatus.FAILED
        if (clearCache) {
            MangaType.clearCacheFor(manga.id, dataFetchingEnvironment)
        }

        return dataFetchingEnvironment.getValueFromDataLoader("MangaDataLoader", manga.id)
    }
}

class CategoryUpdateType(
    val category: CategoryType,
    val status: CategoryJobStatus,
) {
    constructor(job: CategoryUpdateJob) : this(
        CategoryType(job.category),
        when (job.status) {
            CategoryUpdateStatus.UPDATING -> CategoryJobStatus.UPDATING
            CategoryUpdateStatus.SKIPPED -> CategoryJobStatus.SKIPPED
        },
    )
}

// wrap this info in a data class so that the update subscription updates the date of the update status in the clients cache
data class UpdaterJobsInfoType(
    val isRunning: Boolean,
    val totalJobs: Int,
    val finishedJobs: Int,
    val skippedCategoriesCount: Int,
    val skippedMangasCount: Int,
)

data class UpdaterUpdates(
    val categoryUpdates: List<CategoryUpdateType>,
    val mangaUpdates: List<MangaUpdateType>,
    @GraphQLDescription("The current update status at the time of sending the initial message. Is null for all following messages")
    val initial: LibraryUpdateStatus?,
    val jobsInfo: UpdaterJobsInfoType,
    @GraphQLDescription(
        "Indicates whether updates have been omitted based on the \"maxUpdates\" subscription variable. " +
            "In case updates have been omitted, the \"updateStatus\" query should be re-fetched.",
    )
    val omittedUpdates: Boolean,
) {
    constructor(updates: UpdateUpdates, omittedUpdates: Boolean) : this(
        categoryUpdates = updates.categoryUpdates.map(::CategoryUpdateType),
        mangaUpdates = updates.mangaUpdates.map(::MangaUpdateType),
        initial = updates.initial?.let { LibraryUpdateStatus(updates.initial) },
        jobsInfo =
            UpdaterJobsInfoType(
                isRunning = updates.isRunning,
                totalJobs = updates.totalJobs,
                finishedJobs = updates.finishedJobs,
                skippedCategoriesCount = updates.skippedCategoriesCount,
                skippedMangasCount = updates.skippedMangasCount,
            ),
        omittedUpdates = omittedUpdates,
    )
}
