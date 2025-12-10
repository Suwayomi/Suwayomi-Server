package ireader.core.source.simple

import ireader.core.source.model.ChapterInfo

/**
 * Simplified chapter data class
 */
data class Chapter(
    /** Unique identifier - typically the URL */
    val url: String,
    /** Chapter title */
    val title: String,
    /** Chapter number (auto-extracted if -1) */
    val number: Float = -1f,
    /** Upload/release date as epoch milliseconds */
    val date: Long = 0L,
    /** Scanlator/translator group */
    val scanlator: String = "",
    /** Volume number (if applicable) */
    val volume: Int? = null,
    /** Extra metadata */
    val extras: Map<String, String> = emptyMap()
) {
    /**
     * Check if chapter has valid data
     */
    fun isValid(): Boolean = url.isNotBlank() && title.isNotBlank()
    
    /**
     * Check if chapter number is set
     */
    fun hasNumber(): Boolean = number >= 0f
    
    /**
     * Auto-extract chapter number from title if not set
     */
    fun withAutoNumber(): Chapter {
        if (number >= 0f) return this
        return copy(number = extractChapterNumber(title))
    }
    
    /**
     * Convert to legacy ChapterInfo
     */
    fun toChapterInfo(): ChapterInfo = ChapterInfo(
        key = url,
        name = title,
        number = number,
        dateUpload = date,
        scanlator = scanlator
    )
    
    companion object {
        /**
         * Create from legacy ChapterInfo
         */
        fun fromChapterInfo(chapter: ChapterInfo): Chapter = Chapter(
            url = chapter.key,
            title = chapter.name,
            number = chapter.number,
            date = chapter.dateUpload,
            scanlator = chapter.scanlator
        )
        
        /**
         * Extract chapter number from text
         */
        fun extractChapterNumber(text: String): Float {
            val patterns = listOf(
                // English patterns
                Regex("""(?:chapter|ch\.?|episode|ep\.?)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
                // Number at start
                Regex("""^(\d+(?:\.\d+)?)(?:\s*[-:.]|\s+)"""),
                // Chinese: 第X章
                Regex("""第\s*(\d+(?:\.\d+)?)\s*[章话]"""),
                // Korean: X화
                Regex("""(\d+(?:\.\d+)?)\s*화"""),
                // Japanese: 第X話
                Regex("""(\d+(?:\.\d+)?)\s*話"""),
                // Just a number
                Regex("""(\d+(?:\.\d+)?)""")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(text)
                if (match != null) {
                    return match.groupValues[1].toFloatOrNull() ?: -1f
                }
            }
            
            return -1f
        }
        
        /**
         * Create with builder pattern
         */
        inline fun build(url: String, title: String, block: Builder.() -> Unit = {}): Chapter {
            return Builder(url, title).apply(block).build()
        }
    }
    
    class Builder(private val url: String, private val title: String) {
        var number: Float = -1f
        var date: Long = 0L
        var scanlator: String = ""
        var volume: Int? = null
        var extras: Map<String, String> = emptyMap()
        
        fun build(): Chapter = Chapter(
            url = url,
            title = title,
            number = number,
            date = date,
            scanlator = scanlator,
            volume = volume,
            extras = extras
        )
    }
}

/**
 * Sort chapters by number (ascending)
 */
fun List<Chapter>.sortedByNumber(): List<Chapter> = sortedBy { it.number }

/**
 * Sort chapters by number (descending)
 */
fun List<Chapter>.sortedByNumberDescending(): List<Chapter> = sortedByDescending { it.number }

/**
 * Sort chapters by date (newest first)
 */
fun List<Chapter>.sortedByDateDescending(): List<Chapter> = sortedByDescending { it.date }

/**
 * Auto-assign numbers to all chapters
 */
fun List<Chapter>.withAutoNumbers(): List<Chapter> = map { it.withAutoNumber() }

/**
 * Remove duplicate chapters by URL
 */
fun List<Chapter>.distinctByUrl(): List<Chapter> = distinctBy { it.url }
