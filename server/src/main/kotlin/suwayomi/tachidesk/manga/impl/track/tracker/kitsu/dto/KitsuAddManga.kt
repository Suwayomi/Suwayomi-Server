package suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuAddMangaResult(
    val data: KitsuAddMangaItem,
)

@Serializable
data class KitsuAddMangaItem(
    // JSON:API resource ids are strings
    val id: String,
)
