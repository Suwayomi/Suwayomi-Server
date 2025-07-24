package suwayomi.tachidesk.opds.dto

data class OpdsHistoryAcqEntry(
    val chapter: OpdsChapterListAcqEntry,
    val mangaTitle: String,
    val mangaAuthor: String?,
    val mangaId: Int,
    val mangaSourceLang: String?,
    val mangaThumbnailUrl: String?,
)
