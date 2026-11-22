package suwayomi.tachidesk.opds.dto

data class OpdsMangaChapterAcqEntry(
    val chapter: OpdsChapterListAcqEntry,
    val manga: OpdsMangaDetails,
)
