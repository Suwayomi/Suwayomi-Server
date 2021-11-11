package suwayomi.tachidesk.manga.impl.update

import io.javalin.plugin.json.JsonMapper
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

var logger = KotlinLogging.logger {}
class UpdateStatus(
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

    fun getSummary(): UpdateStatusSummary {
        val summaryMap = mutableMapOf<JobStatus, Int>()
        statusMap.forEach {
            summaryMap[it.key] = it.value.size
        }
        return UpdateStatusSummary(
            running,
            summaryMap
        )
    }

    // serialize to summary json
    fun getJsonSummary(): String {
        val jsonMapper by DI.global.instance<JsonMapper>()
        return jsonMapper.toJsonString(getSummary())
    }
}
