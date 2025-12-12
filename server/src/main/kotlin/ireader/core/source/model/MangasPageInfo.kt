
package ireader.core.source.model

import kotlinx.serialization.Serializable

/**
 * Paginated list of manga results.
 * 
 * This class is serializable for iOS JS bridge support.
 */
@Serializable
data class MangasPageInfo(
    val mangas: List<MangaInfo>,
    val hasNextPage: Boolean
) {
    companion object {
        /**
         * Create empty page info
         */
        fun empty(): MangasPageInfo {
            return MangasPageInfo(emptyList(), false)
        }
        
        /**
         * Create page info with no next page
         */
        fun lastPage(mangas: List<MangaInfo>): MangasPageInfo {
            return MangasPageInfo(mangas, false)
        }
    }
    
    /**
     * Check if page is empty
     */
    fun isEmpty(): Boolean = mangas.isEmpty()
    
    /**
     * Check if page has content
     */
    fun isNotEmpty(): Boolean = mangas.isNotEmpty()
    
    /**
     * Get manga count
     */
    fun size(): Int = mangas.size
    
    /**
     * Filter mangas by predicate
     */
    fun filter(predicate: (MangaInfo) -> Boolean): MangasPageInfo {
        return copy(mangas = mangas.filter(predicate))
    }
    
    /**
     * Map mangas with transform
     */
    fun map(transform: (MangaInfo) -> MangaInfo): MangasPageInfo {
        return copy(mangas = mangas.map(transform))
    }
}
