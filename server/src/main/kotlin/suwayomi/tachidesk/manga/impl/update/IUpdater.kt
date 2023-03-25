package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.flow.StateFlow
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

interface IUpdater {
    fun addMangasToQueue(mangas: List<MangaDataClass>)
    val status: StateFlow<UpdateStatus>
    fun reset()
}
