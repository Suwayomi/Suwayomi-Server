package suwayomi.tachidesk.manga.impl.update

import mu.KotlinLogging
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

var logger = KotlinLogging.logger {}
class UpdateStatus (
    var statusMap: MutableMap<JobStatus, MutableList<MangaDataClass>> = mutableMapOf<JobStatus, MutableList<MangaDataClass>>(),
    var running: Boolean = false,
    ) {
    var numberOfJobs: Int = 0

    constructor(jobs: List<UpdateJob>, running: Boolean) : this(
        mutableMapOf<JobStatus, MutableList<MangaDataClass>>(),
        running
    ) {
        this.numberOfJobs = jobs.size
        jobs.forEach {
            val list = statusMap.getOrDefault(it.status, mutableListOf())
            list.add(it.manga)
            statusMap[it.status] = list
        }
    }


    override fun toString(): String {
        return "UpdateStatus(statusMap=${statusMap.map { "${it.key} : ${it.value.size}" }.joinToString("; ")}, running=$running)"
    }

    // serialize to summary json
    fun getJsonSummary(): String {
        return """{"statusMap":{${statusMap.map { "\"${it.key}\" : ${it.value.size}" }.joinToString(",")}}, "running":$running}"""
    }
}
