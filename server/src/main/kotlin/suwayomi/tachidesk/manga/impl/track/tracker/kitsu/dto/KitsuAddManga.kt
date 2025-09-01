package suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuAddMangaResult(
    val data: KitsuAddMangaItem,
)

@Serializable
data class KitsuAddMangaItem(
    val id: Long,
)
