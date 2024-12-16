@file:Suppress("ktlint:standard:property-naming")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable

interface SManga : Serializable {
    var url: String

    var title: String

    var artist: String?

    var author: String?

    var description: String?

    var genre: String?

    var status: Int

    var thumbnail_url: String?

    var update_strategy: UpdateStrategy

    var initialized: Boolean

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SManga = SMangaImpl()
    }
}

// fun SManga.toMangaInfo(): MangaInfo {
//    return MangaInfo(
//        key = this.url,
//        title = this.title,
//        artist = this.artist ?: "",
//        author = this.author ?: "",
//        description = this.description ?: "",
//        genres = this.genre?.split(", ") ?: emptyList(),
//        status = this.status,
//        cover = this.thumbnail_url ?: ""
//    )
// }
//
// fun MangaInfo.toSManga(): SManga {
//    val mangaInfo = this
//    return SManga.create().apply {
//        url = mangaInfo.key
//        title = mangaInfo.title
//        artist = mangaInfo.artist
//        author = mangaInfo.author
//        description = mangaInfo.description
//        genre = mangaInfo.genres.joinToString(", ")
//        status = mangaInfo.status
//        thumbnail_url = mangaInfo.cover
//    }
// }
