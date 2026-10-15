package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto

import kotlinx.serialization.Serializable

@Serializable
data class MALUserSearchResult(
    val data: List<MALUserSearchItem> = emptyList(),
    val paging: MALUserSearchPaging = MALUserSearchPaging(),
)

@Serializable
data class MALUserSearchItem(
    val node: MALUserSearchItemNode,
)

@Serializable
data class MALUserSearchPaging(
    val next: String? = null,
)

@Serializable
data class MALUserSearchItemNode(
    val id: Int,
    val title: String = "",
)
