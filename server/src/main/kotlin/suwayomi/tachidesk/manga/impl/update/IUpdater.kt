package suwayomi.tachidesk.manga.impl.update

import rx.Observable
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

interface IUpdater {
    fun addMangaToQueue(manga: MangaDataClass)
    fun listJobs(): List<UpdateJob>
    fun startUpdater()
    fun resetUpdater()
    fun isUpdaterRunning(): Boolean
    fun getStatus(): Observable<UpdateStatus>
}
