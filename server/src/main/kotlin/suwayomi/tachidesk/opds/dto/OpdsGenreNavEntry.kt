package suwayomi.tachidesk.opds.dto

data class OpdsGenreNavEntry(
    val id: String, // Raw genre name (e.g., "Action & Adventure"); callers encode it per URL context
    val title: String, // e.g., "Action & Adventure"
    val mangaCount: Long,
)
