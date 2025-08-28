package suwayomi.tachidesk.graphql.types

// These types belong to SettingsType.kt. However, since that file is auto-generated, these types need to be placed in
// a "static" file.

data class DownloadConversion(
    val target: String,
    val compressionLevel: Double? = null,
)

interface SettingsDownloadConversion {
    val mimeType: String
    val target: String
    val compressionLevel: Double?
}

class SettingsDownloadConversionType(
    override val mimeType: String,
    override val target: String,
    override val compressionLevel: Double?,
) : SettingsDownloadConversion
