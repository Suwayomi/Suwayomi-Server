package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass

interface IUpdater {
    fun getLastUpdateTimestamp(): Long

    fun addCategoriesToUpdateQueue(
        categories: List<CategoryDataClass>,
        clear: Boolean?,
        forceAll: Boolean,
    )

    val status: Flow<UpdateStatus>

    val statusDeprecated: StateFlow<UpdateStatus>

    fun reset()
}
