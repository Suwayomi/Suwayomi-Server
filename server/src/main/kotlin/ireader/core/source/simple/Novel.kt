package ireader.core.source.simple

import ireader.core.source.model.MangaInfo

/**
 * Simplified novel data class
 * Replaces MangaInfo with clearer naming and easier construction
 */
data class Novel(
    /** Unique identifier - typically the URL */
    val url: String,
    /** Novel title */
    val title: String,
    /** Author name(s) */
    val author: String = "",
    /** Artist name(s) - for manga/manhwa */
    val artist: String = "",
    /** Novel description/synopsis */
    val description: String = "",
    /** Genre tags */
    val genres: List<String> = emptyList(),
    /** Publication status */
    val status: NovelStatus = NovelStatus.UNKNOWN,
    /** Cover image URL */
    val cover: String = "",
    /** Alternative titles */
    val alternativeTitles: List<String> = emptyList(),
    /** Rating (0-10 scale) */
    val rating: Float? = null,
    /** View count */
    val views: Long? = null,
    /** Extra metadata */
    val extras: Map<String, String> = emptyMap()
) {
    /**
     * Check if novel has minimum required data
     */
    fun isValid(): Boolean = url.isNotBlank() && title.isNotBlank()
    
    /**
     * Get cleaned description without excessive whitespace
     */
    fun cleanDescription(): String = description
        .replace(Regex("\\s+"), " ")
        .trim()
    
    /**
     * Convert to legacy MangaInfo for compatibility
     */
    fun toMangaInfo(): MangaInfo = MangaInfo(
        key = url,
        title = title,
        author = author,
        artist = artist,
        description = description,
        genres = genres,
        status = status.toLegacyStatus(),
        cover = cover
    )
    
    companion object {
        /**
         * Create from legacy MangaInfo
         */
        fun fromMangaInfo(manga: MangaInfo): Novel = Novel(
            url = manga.key,
            title = manga.title,
            author = manga.author,
            artist = manga.artist,
            description = manga.description,
            genres = manga.genres,
            status = NovelStatus.fromLegacy(manga.status),
            cover = manga.cover
        )
        
        /**
         * Create with builder pattern
         */
        inline fun build(url: String, title: String, block: Builder.() -> Unit = {}): Novel {
            return Builder(url, title).apply(block).build()
        }
    }
    
    class Builder(private val url: String, private val title: String) {
        var author: String = ""
        var artist: String = ""
        var description: String = ""
        var genres: List<String> = emptyList()
        var status: NovelStatus = NovelStatus.UNKNOWN
        var cover: String = ""
        var alternativeTitles: List<String> = emptyList()
        var rating: Float? = null
        var views: Long? = null
        var extras: Map<String, String> = emptyMap()
        
        fun build(): Novel = Novel(
            url = url,
            title = title,
            author = author,
            artist = artist,
            description = description,
            genres = genres,
            status = status,
            cover = cover,
            alternativeTitles = alternativeTitles,
            rating = rating,
            views = views,
            extras = extras
        )
    }
}
