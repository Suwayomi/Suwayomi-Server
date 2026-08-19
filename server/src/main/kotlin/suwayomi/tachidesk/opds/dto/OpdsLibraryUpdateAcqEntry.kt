package suwayomi.tachidesk.opds.dto

data class OpdsLibraryUpdateAcqEntry(
    val chapter: OpdsChapterListAcqEntry,
    val mangaTitle: String,
    val mangaAuthor: String?,
    val mangaId: Int,
    val mangaTotalChapters: Long,
    val mangaSourceLang: String?,
    val mangaThumbnailUrl: String?,
)
