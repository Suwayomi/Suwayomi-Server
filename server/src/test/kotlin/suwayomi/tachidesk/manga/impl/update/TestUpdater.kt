package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

class TestUpdater : IUpdater {
    //     private val updateQueue = CopyOnWriteArrayList<UpdateJob>()
//     private var isRunning = false
//     private val _status = MutableStateFlow(UpdateStatus())
//     override val status: StateFlow<UpdateStatus> = _status.asStateFlow()
//
//     override fun addMangasToQueue(mangas: List<MangaDataClass>) {
//         mangas.forEach { updateQueue.add(UpdateJob(it)) }
//         isRunning = true
//         updateStatus()
//     }
//
//     override fun reset() {
//         updateQueue.clear()
//         isRunning = false
//         updateStatus()
//     }
//
//     private fun updateStatus() {
//         _status.update { UpdateStatus(updateQueue.toList(), isRunning) }
//     }
    override fun getLastUpdateTimestamp(): Long {
        TODO("Not yet implemented")
    }

    override fun addCategoriesToUpdateQueue(
        categories: List<CategoryDataClass>,
        clear: Boolean?,
        forceAll: Boolean,
    ) {
        TODO("Not yet implemented")
    }

    override fun addMangasToQueue(mangas: List<MangaDataClass>) {
        TODO("Not yet implemented")
    }

    override val status: Flow<UpdateStatus>
        get() = TODO("Not yet implemented")
    override val updates: Flow<UpdateUpdates>
        get() = TODO("Not yet implemented")
    override val statusDeprecated: StateFlow<UpdateStatus>
        get() = TODO("Not yet implemented")

    override fun reset() {
        TODO("Not yet implemented")
    }

    override fun getStatus(): UpdateUpdates {
        TODO("Not yet implemented")
    }
}
