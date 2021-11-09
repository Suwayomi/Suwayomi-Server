package suwayomi.tachidesk.manga.impl.update

import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED
}

class UpdateJob(val manga: MangaDataClass, var status: JobStatus = JobStatus.PENDING) {

    override fun toString(): String {
        return "UpdateJob(status=$status, manga=${manga.title})"
    }
}
