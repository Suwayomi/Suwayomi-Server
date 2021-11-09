package suwayomi.tachidesk.manga.impl.update

import mu.KotlinLogging
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

var logger = KotlinLogging.logger {}
class UpdateStatus {
    var statusMap = HashMap<JobStatus, ArrayList<MangaDataClass>>()
    var running: Boolean = false

    constructor(jobs: List<UpdateJob>, running: Boolean) {
        logger.info { "Recreating" }
        this.running = running
        jobs.forEach {
            val list = statusMap.getOrDefault(it.status, ArrayList())
            list.add(it.manga)
            statusMap[it.status] = list
        }
    }

    constructor()
}
