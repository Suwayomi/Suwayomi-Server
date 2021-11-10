package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

class TestUpdater : IUpdater {
    private val updateQueue = ArrayList<UpdateJob>()
    private var isRunning = false

    override fun addMangaToQueue(manga: MangaDataClass) {
        updateQueue.add(UpdateJob(manga))
        isRunning = true
    }

    override fun getStatus(): StateFlow<UpdateStatus> {
        return MutableStateFlow(UpdateStatus(updateQueue, isRunning))
    }

    override suspend fun reset() {
        updateQueue.clear()
        isRunning = false
    }
}
