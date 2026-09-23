package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import suwayomi.tachidesk.graphql.types.SourceContentType
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

interface IUpdater {
    fun getLastUpdateTimestamp(): Long

    fun getLastContentUpdateTimestamp(contentType: SourceContentType): Long = getLastUpdateTimestamp()

    fun deleteLastAutomatedUpdateTimestamp()

    fun addCategoriesToUpdateQueue(
        categories: List<CategoryDataClass>,
        clear: Boolean?,
        forceAll: Boolean,
        contentType: SourceContentType? = null,
    )

    fun addMangasToQueue(mangas: List<MangaDataClass>)

    @Deprecated("Replaced with updates", replaceWith = ReplaceWith("updates"))
    val status: Flow<UpdateStatus>

    val updates: Flow<UpdateUpdates>

    val statusDeprecated: StateFlow<UpdateStatus>

    fun reset()

    fun getStatus(): UpdateUpdates
}
