package ir.armor.tachidesk.database.dataclass

data class ExtensionDataClass(
    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val isNsfw: Boolean,
    val apkName: String,
    val iconUrl: String,
    val installed: Boolean,
    val classFQName: String,
)
