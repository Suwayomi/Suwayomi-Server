package suwayomi.tachidesk.opds.dto

data class OpdsMangaDetails( // Kept name, it's specific enough
    val id: Int,
    val title: String,
    val thumbnailUrl: String?,
    val author: String?, // Added for chapter entry authors
)
