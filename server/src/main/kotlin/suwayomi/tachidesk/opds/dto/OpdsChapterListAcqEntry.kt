package suwayomi.tachidesk.opds.dto

data class OpdsChapterListAcqEntry(
    val id: Int,
    val mangaId: Int,
    val name: String,
    val uploadDate: Long,
    val chapterNumber: Float,
    val scanlator: String?,
    val read: Boolean,
    val lastPageRead: Int,
    val sourceOrder: Int,
    val pageCount: Int, // Can be -1 if not known
)
