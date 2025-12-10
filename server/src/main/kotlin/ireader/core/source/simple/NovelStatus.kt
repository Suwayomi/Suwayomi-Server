package ireader.core.source.simple

import ireader.core.source.model.MangaInfo

/**
 * Novel publication status
 */
enum class NovelStatus {
    UNKNOWN,
    ONGOING,
    COMPLETED,
    LICENSED,
    CANCELLED,
    ON_HIATUS,
    PUBLISHING_FINISHED;
    
    /**
     * Convert to legacy status code
     */
    fun toLegacyStatus(): Long = when (this) {
        UNKNOWN -> MangaInfo.UNKNOWN
        ONGOING -> MangaInfo.ONGOING
        COMPLETED -> MangaInfo.COMPLETED
        LICENSED -> MangaInfo.LICENSED
        CANCELLED -> MangaInfo.CANCELLED
        ON_HIATUS -> MangaInfo.ON_HIATUS
        PUBLISHING_FINISHED -> MangaInfo.PUBLISHING_FINISHED
    }
    
    /**
     * Check if novel is still being published
     */
    fun isOngoing(): Boolean = this == ONGOING
    
    /**
     * Check if novel is finished
     */
    fun isFinished(): Boolean = this == COMPLETED || this == PUBLISHING_FINISHED
    
    companion object {
        /**
         * Parse status from common text values
         */
        fun parse(text: String): NovelStatus {
            val normalized = text.trim().lowercase()
            return when {
                normalized in listOf("ongoing", "publishing", "serializing", "active", "updating") -> ONGOING
                normalized in listOf("completed", "complete", "finished", "ended") -> COMPLETED
                normalized in listOf("licensed") -> LICENSED
                normalized in listOf("cancelled", "canceled", "dropped", "discontinued") -> CANCELLED
                normalized in listOf("hiatus", "on hiatus", "on hold", "paused") -> ON_HIATUS
                else -> UNKNOWN
            }
        }
        
        /**
         * Convert from legacy status code
         */
        fun fromLegacy(status: Long): NovelStatus = when (status) {
            MangaInfo.ONGOING -> ONGOING
            MangaInfo.COMPLETED -> COMPLETED
            MangaInfo.LICENSED -> LICENSED
            MangaInfo.CANCELLED -> CANCELLED
            MangaInfo.ON_HIATUS -> ON_HIATUS
            MangaInfo.PUBLISHING_FINISHED -> PUBLISHING_FINISHED
            else -> UNKNOWN
        }
    }
}

/**
 * Extension to parse status from string
 */
fun String.toNovelStatus(): NovelStatus = NovelStatus.parse(this)
