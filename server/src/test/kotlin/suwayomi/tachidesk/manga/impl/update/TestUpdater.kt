package suwayomi.tachidesk.manga.impl.update

import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

class TestUpdater : IUpdater {
    private val updateQueue = ArrayList<UpdateJob>()
    private var isRunning = false

    override fun addMangaToQueue(manga: MangaDataClass) {
        updateQueue.add(UpdateJob(manga))
    }

    override fun listJobs(): List<UpdateJob> {
        return updateQueue
    }

    override fun startUpdater() {
        isRunning = true
    }

    override fun resetUpdater() {
        updateQueue.clear()
        isRunning = false
    }

    override fun isUpdaterRunning(): Boolean {
        return isRunning
    }
}
