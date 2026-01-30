
package ireader.core.source.model

import kotlinx.serialization.Serializable

/**
 * Model for a chapter given by a source.
 * 
 * This class is serializable for iOS JS bridge support.
 */
@Serializable
data class ChapterInfo(
    var key: String,
    var name: String,
    var dateUpload: Long = 0,
    var number: Float = -1f,
    var scanlator: String = "",
    var type: Long = ChapterInfo.NOVEL
) {
    companion object {
        const val MIX = 0L
        const val NOVEL = 1L
        const val MUSIC = 2L
        const val MANGA = 3L
        const val MOVIE = 4L
        
        /**
         * Extract chapter number from chapter name using common patterns
         */
        fun extractChapterNumber(name: String): Float {
            val patterns = listOf(
                Regex("""(?:chapter|ch\.?|episode|ep\.?)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
                Regex("""^(\d+(?:\.\d+)?)(?:\s*[-:.]|\s+)"""),
                Regex("""第\s*(\d+(?:\.\d+)?)\s*[章话]"""), // Chinese
                Regex("""(\d+(?:\.\d+)?)\s*화"""), // Korean
                Regex("""(\d+(?:\.\d+)?)\s*話""") // Japanese
            )
            
            for (pattern in patterns) {
                val match = pattern.find(name)
                if (match != null) {
                    return match.groupValues[1].toFloatOrNull() ?: -1f
                }
            }
            return -1f
        }
    }
    
    /**
     * Check if chapter has a valid number
     */
    fun hasValidNumber(): Boolean = number >= 0f
    
    /**
     * Check if chapter is a novel type
     */
    fun isNovel(): Boolean = type == NOVEL
    
    /**
     * Validate that required fields are present
     */
    fun isValid(): Boolean = key.isNotBlank() && name.isNotBlank()
    
    /**
     * Auto-extract chapter number if not set
     */
    fun withAutoNumber(): ChapterInfo {
        return if (number < 0f) {
            copy(number = extractChapterNumber(name))
        } else {
            this
        }
    }
}
