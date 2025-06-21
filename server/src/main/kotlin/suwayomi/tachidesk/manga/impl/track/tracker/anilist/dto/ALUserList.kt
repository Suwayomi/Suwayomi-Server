package suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALUserListMangaQueryResult(
    val data: ALUserListMangaPage,
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
data class ALUserListItem(
    val id: Long,
    val status: String,
    val scoreRaw: Int,
    val progress: Int,
    val startedAt: ALFuzzyDate,
    val completedAt: ALFuzzyDate,
    val media: ALSearchItem,
    val private: Boolean,
) {
    fun toALUserManga(): ALUserManga =
        ALUserManga(
            libraryId = this@ALUserListItem.id,
            listStatus = status,
            scoreRaw = scoreRaw,
            chaptersRead = progress,
            startDateFuzzy = startedAt.toEpochMilli(),
            completedDateFuzzy = completedAt.toEpochMilli(),
            manga = media.toALManga(),
            private = private,
        )
}
