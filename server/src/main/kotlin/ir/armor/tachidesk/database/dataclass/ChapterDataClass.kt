package ir.armor.tachidesk.database.dataclass

data class ChapterDataClass(
        val url: String,
        val name: String,
        val date_upload: String,
        val chapter_number: Float,
        val scanlator: String?,
)