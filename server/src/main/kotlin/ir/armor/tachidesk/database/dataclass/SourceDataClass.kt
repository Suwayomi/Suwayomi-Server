package ir.armor.tachidesk.database.dataclass

data class SourceDataClass(
        val id: Long,
        val name: String,
        val lang: String,
        val iconUrl: String,
        val supportsLatest: Boolean
)