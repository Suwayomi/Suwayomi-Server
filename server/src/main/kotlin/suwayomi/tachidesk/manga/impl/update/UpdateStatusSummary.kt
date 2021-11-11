package suwayomi.tachidesk.manga.impl.update

data class UpdateStatusSummary(
    val running: Boolean,
    val statusMap: Map<JobStatus, Int>
)
