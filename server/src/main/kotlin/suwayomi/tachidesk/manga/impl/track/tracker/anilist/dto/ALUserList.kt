package suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALUserListMangaQueryResult(
    // absent when AniList answers with an `errors`-only payload
    val data: ALUserListMangaPage? = null,
)

@Serializable
data class ALUserListMangaPage(
    @SerialName("Page")
    val page: ALUserListMediaList,
)

@Serializable
data class ALUserListMediaList(
    val mediaList: List<ALUserListItem>,
)

@Serializable
// The AniList schema marks nearly every field nullable; missing fields used
// to blow up decoding with MissingFieldException -> 500.
data class ALUserListItem(
    val id: Long,
    val status: String? = null,
    val scoreRaw: Int? = null,
    val progress: Int? = null,
    val startedAt: ALFuzzyDate = ALFuzzyDate(),
    val completedAt: ALFuzzyDate = ALFuzzyDate(),
    val media: ALSearchItem? = null,
    val private: Boolean = false,
) {
    fun toALUserManga(): ALUserManga? =
        media?.let {
            ALUserManga(
                libraryId = this@ALUserListItem.id,
                listStatus = status ?: "",
                scoreRaw = scoreRaw ?: 0,
                chaptersRead = progress ?: 0,
                startDateFuzzy = startedAt.toEpochMilli(),
                completedDateFuzzy = completedAt.toEpochMilli(),
                manga = it.toALManga(),
                private = private,
            )
        }
}
