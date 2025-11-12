

package ireader.core.source.model

/**
 * Model for a manga given by a source
 *
 */
data class MangaInfo(
    val key: String,
    val title: String,
    val artist: String = "",
    val author: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val status: Long = UNKNOWN,
    val cover: String = "",
) {
    companion object {
        const val UNKNOWN = 0L
        const val ONGOING = 1L
        const val COMPLETED = 2L
        const val LICENSED = 3L
        const val PUBLISHING_FINISHED = 4L
        const val CANCELLED = 5L
        const val ON_HIATUS = 6L
    }
}
