package suwayomi.tachidesk.manga.impl.update

import mu.KotlinLogging
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

var logger = KotlinLogging.logger {}
class UpdateStatus {
    var statusMap = HashMap<JobStatus, ArrayList<MangaDataClass>>()
    var running: Boolean = false

    constructor(jobs: List<UpdateJob>, running: Boolean) {
        this.running = running
        jobs.forEach {
            val list = statusMap.getOrDefault(it.status, ArrayList())
            list.add(it.manga)
            statusMap[it.status] = list
        }
    }

    constructor()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UpdateStatus

        if (statusMap != other.statusMap) return false
        if (running != other.running) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusMap.hashCode()
        result = 31 * result + running.hashCode()
        return result
    }

    override fun toString(): String {
        return "UpdateStatus(statusMap=${statusMap.map { "${it.key} : ${it.value.size}" }.joinToString("; ")}, running=$running)"
    }

    // serialize to json
    fun toJson(): String {
        return """{"statusMap":{${statusMap.map { "\"${it.key}\" : ${it.value.size}" }.joinToString(",")}}, "running":$running}"""
    }
}
