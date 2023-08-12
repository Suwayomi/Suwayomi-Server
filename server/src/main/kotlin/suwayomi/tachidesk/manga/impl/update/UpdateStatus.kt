package suwayomi.tachidesk.manga.impl.update

import com.fasterxml.jackson.annotation.JsonIgnore
import mu.KotlinLogging
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

val logger = KotlinLogging.logger {}
data class UpdateStatus(
    val mangaStatusMap: Map<JobStatus, List<MangaDataClass>> = emptyMap(),
    val running: Boolean = false,
    @JsonIgnore
    val numberOfJobs: Int = 0
) {

    constructor(jobs: List<UpdateJob>, skippedMangas: List<MangaDataClass>, running: Boolean) : this(
        mangaStatusMap = jobs.groupBy { it.status }
            .mapValues { entry ->
                entry.value.map { it.manga }
            }.plus(Pair(JobStatus.SKIPPED, skippedMangas)),
        running = running,
        numberOfJobs = jobs.size
    )
}
