package suwayomi.tachidesk.manga.impl.update

import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

interface IUpdater {
    fun addManga(manga: MangaDataClass): Unit
    fun getCurrentJobs(): List<MangaDataClass>
    fun getProgress(): Pair<Int, Int>
    fun isUpdating(): Boolean
    fun start(): Unit
    fun cancel(): Unit
}
