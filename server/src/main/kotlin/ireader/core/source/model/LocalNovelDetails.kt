package ireader.core.source.model

import kotlinx.serialization.Serializable

/**
 * Metadata structure for details.json in local novel folders
 * Allows users to manually add rich metadata
 */
@Serializable
data class LocalNovelDetails(
    val title: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: String? = null  // "Ongoing", "Completed", "Hiatus", etc.
) {
    fun toStatus(): Long {
        return when (status?.lowercase()) {
            "ongoing" -> MangaInfo.ONGOING
            "completed" -> MangaInfo.COMPLETED
            "licensed" -> MangaInfo.LICENSED
            "finished", "publishing_finished" -> MangaInfo.PUBLISHING_FINISHED
            "cancelled" -> MangaInfo.CANCELLED
            "hiatus", "on_hiatus" -> MangaInfo.ON_HIATUS
            else -> MangaInfo.UNKNOWN
        }
    }
}
