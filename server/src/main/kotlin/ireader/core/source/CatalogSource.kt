

package ireader.core.source

import ireader.core.source.model.CommandList
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangasPageInfo

interface CatalogSource : ireader.core.source.Source {
    companion object {
        const val TYPE_NOVEL = 0
        const val TYPE_MANGA = 1
        const val TYPE_MOVIE = 2
    }

    override val lang: String

    suspend fun getMangaList(
        sort: Listing?,
        page: Int,
    ): MangasPageInfo

    suspend fun getMangaList(
        filters: FilterList,
        page: Int,
    ): MangasPageInfo

    fun getListings(): List<Listing>

    fun getFilters(): FilterList

    fun getCommands(): CommandList
}
