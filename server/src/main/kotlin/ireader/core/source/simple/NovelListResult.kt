package ireader.core.source.simple

import ireader.core.source.model.MangasPageInfo

/**
 * Result of a novel list query (search, popular, latest, etc.)
 */
data class NovelListResult(
    /** List of novels */
    val novels: List<Novel>,
    /** Whether there are more pages */
    val hasNextPage: Boolean
) {
    /**
     * Check if result is empty
     */
    fun isEmpty(): Boolean = novels.isEmpty()
    
    /**
     * Check if result has content
     */
    fun isNotEmpty(): Boolean = novels.isNotEmpty()
    
    /**
     * Get novel count
     */
    val size: Int get() = novels.size
    
    /**
     * Filter novels
     */
    fun filter(predicate: (Novel) -> Boolean): NovelListResult {
        return copy(novels = novels.filter(predicate))
    }
    
    /**
     * Map novels
     */
    fun map(transform: (Novel) -> Novel): NovelListResult {
        return copy(novels = novels.map(transform))
    }
    
    /**
     * Convert to legacy MangasPageInfo
     */
    fun toMangasPageInfo(): MangasPageInfo = MangasPageInfo(
        mangas = novels.map { it.toMangaInfo() },
        hasNextPage = hasNextPage
    )
    
    companion object {
        /**
         * Empty result
         */
        fun empty(): NovelListResult = NovelListResult(emptyList(), false)
        
        /**
         * Single novel result
         */
        fun single(novel: Novel): NovelListResult = NovelListResult(listOf(novel), false)
        
        /**
         * Last page (no more results)
         */
        fun lastPage(novels: List<Novel>): NovelListResult = NovelListResult(novels, false)
        
        /**
         * Create from legacy MangasPageInfo
         */
        fun fromMangasPageInfo(page: MangasPageInfo): NovelListResult = NovelListResult(
            novels = page.mangas.map { Novel.fromMangaInfo(it) },
            hasNextPage = page.hasNextPage
        )
    }
}
