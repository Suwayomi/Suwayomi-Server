package ir.armor.tachidesk.database.dataclass

data class ChapterDataClass(
        val id: Int,
        val url: String,
        val name: String,
        val date_upload: Long,
        val chapter_number: Float,
        val scanlator: String?,
        val mangaId: Int,
)