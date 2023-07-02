package suwayomi.tachidesk.graphql.queries

import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.JobStatus
import suwayomi.tachidesk.manga.impl.update.UpdaterSocket
import suwayomi.tachidesk.manga.model.table.MangaTable

class UpdaterQuery {
    sealed interface UpdaterStatus {
        data class UpdaterJob(val status: JobStatus, val manga: MangaType)

        data class Running(val jobs: Map<JobStatus, List<MangaType>>) : UpdaterStatus

        // data class Idle
    }

    private val updater by DI.global.instance<IUpdater>()

    fun updaterStatus() {
        val status = updater.status.value
        if (status.running) {
            val mangaIds = status.statusMap.values.flatMap { mangas -> mangas.map { it.id } }
            val mangaMap = transaction {
                MangaTable.select { MangaTable.id inList mangaIds }
                    .map { MangaType(it) }
                    .associateBy { it.id }
            }
            UpdaterStatus.Running(
                status.statusMap.mapValues { (_, mangas) ->
                    mangas.mapNotNull { mangaMap[it.id] }
                }
            )
        }
        UpdaterSocket
    }
}
