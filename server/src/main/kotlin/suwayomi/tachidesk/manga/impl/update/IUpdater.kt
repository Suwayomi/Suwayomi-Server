package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.flow.StateFlow
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass

interface IUpdater {
    fun getLastUpdateTimestamp(): Long

    fun addCategoriesToUpdateQueue(
        categories: List<CategoryDataClass>,
        clear: Boolean?,
        forceAll: Boolean,
    )

    val status: StateFlow<UpdateStatus>

    fun reset()
}
