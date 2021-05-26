package suwayomi.tachidesk.impl.extension.github

data class OnlineExtension(
    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val isNsfw: Boolean,
    val apkName: String,
    val iconUrl: String
)
