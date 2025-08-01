package suwayomi.tachidesk.opds.dto

data class OpdsMangaAcqEntry(
    val id: Int,
    val title: String,
    val thumbnailUrl: String?,
    val lastFetchedAt: Long,
    val inLibrary: Boolean,
    val author: String?,
    val genres: List<String>,
    val description: String?,
    val status: Int,
    val sourceName: String,
    val sourceLang: String,
    val url: String?,
)
