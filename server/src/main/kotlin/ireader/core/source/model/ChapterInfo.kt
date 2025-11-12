

package ireader.core.source.model

data class ChapterInfo(
    var key: String,
    var name: String,
    var dateUpload: Long = 0,
    var number: Float = -1f,
    var scanlator: String = "",
    var type: Long = ChapterInfo.NOVEL,
) {
    companion object {
        const val MIX = 0L
        const val NOVEL = 1L
        const val MUSIC = 2L
        const val MANGA = 3L
        const val MOVIE = 4L
    }
}
