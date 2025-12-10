
package ireader.core.source.model

import kotlinx.serialization.Serializable

/**
 * Model for a manga given by a source.
 * 
 * This class is serializable for iOS JS bridge support.
 */
@Serializable
data class MangaInfo(
    val key: String,
    val title: String,
    val artist: String = "",
    val author: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val status: Long = UNKNOWN,
    val cover: String = ""
) {

    companion object {
        const val UNKNOWN = 0L
        const val ONGOING = 1L
        const val COMPLETED = 2L
        const val LICENSED = 3L
        const val PUBLISHING_FINISHED = 4L
        const val CANCELLED = 5L
        const val ON_HIATUS = 6L
        
        /**
         * Helper to parse status from common string values
         */
        fun parseStatus(statusText: String): Long {
            return when (statusText.trim().lowercase()) {
                "ongoing", "publishing", "serializing" -> ONGOING
                "completed", "complete", "finished" -> COMPLETED
                "licensed" -> LICENSED
                "cancelled", "canceled", "dropped" -> CANCELLED
                "hiatus", "on hiatus", "on hold" -> ON_HIATUS
                else -> UNKNOWN
            }
        }
    }
    
    /**
     * Check if the manga is still being published
     */
    fun isOngoing(): Boolean = status == ONGOING
    
    /**
     * Check if the manga is completed
     */
    fun isCompleted(): Boolean = status == COMPLETED || status == PUBLISHING_FINISHED
    
    /**
     * Validate that required fields are present
     */
    fun isValid(): Boolean = key.isNotBlank() && title.isNotBlank()
    
    /**
     * Get a cleaned description without excessive whitespace
     */
    fun getCleanDescription(): String = description
        .replace(Regex("\\s+"), " ")
        .trim()
}
