package suwayomi.tachidesk.opds.dto

data class OpdsMangaAcqEntry(
    val id: Int,
    val title: String,
    val author: String?,
    val genres: List<String>, // Raw genres, will be processed in builder
    val description: String?,
    val thumbnailUrl: String?, // Raw thumbnail URL from DB
    val sourceLang: String?,
    val inLibrary: Boolean,
)
