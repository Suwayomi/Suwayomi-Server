package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.flow.StateFlow
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

interface IUpdater {
    fun addMangaToQueue(manga: MangaDataClass)
    fun getStatus(): StateFlow<UpdateStatus>
    suspend fun reset(): Unit
}
