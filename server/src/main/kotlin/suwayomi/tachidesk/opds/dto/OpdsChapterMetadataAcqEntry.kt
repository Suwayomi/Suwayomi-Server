package suwayomi.tachidesk.opds.dto

data class OpdsChapterMetadataAcqEntry(
    val id: Int,
    val mangaId: Int,
    val name: String,
    val uploadDate: Long,
    val scanlator: String?,
    val read: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
    val sourceOrder: Int,
    val downloaded: Boolean,
    val pageCount: Int,
    val url: String?,
    val cbzFileSize: Long? = null,
)
