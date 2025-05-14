package suwayomi.tachidesk.manga.impl.update

import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED,
    SKIPPED,
}

data class UpdateJob(
    val manga: MangaDataClass,
    val status: JobStatus = JobStatus.PENDING,
)

enum class CategoryUpdateStatus {
    UPDATING,
    SKIPPED,
}

data class CategoryUpdateJob(
    val category: CategoryDataClass,
    val status: CategoryUpdateStatus,
)
