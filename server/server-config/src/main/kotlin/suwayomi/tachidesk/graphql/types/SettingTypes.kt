package suwayomi.tachidesk.graphql.types

import kotlin.time.Duration

// These types belong to SettingsType.kt. However, since that file is auto-generated, these types need to be placed in
// a "static" file.

data class DownloadConversionHeader(
    val name: String,
    val value: String,
)

class DownloadConversion(
    val target: String,
    val compressionLevel: Double? = null,
    val callTimeout: Duration? = null,
    val connectTimeout: Duration? = null,
    val headers: HeaderList? = null,
)

class HeaderList(
    list: List<DownloadConversionHeader>,
) : List<DownloadConversionHeader> by list

interface SettingsDownloadConversion {
    val mimeType: String
    val target: String
    val compressionLevel: Double?
    val callTimeout: Duration?
    val connectTimeout: Duration?
    val headers: List<SettingsDownloadConversionHeader>?
}

class SettingsDownloadConversionType(
    override val mimeType: String,
    override val target: String,
    override val compressionLevel: Double?,
    override val callTimeout: Duration?,
    override val connectTimeout: Duration?,
    override val headers: List<SettingsDownloadConversionHeaderType>?
) : SettingsDownloadConversion

interface SettingsDownloadConversionHeader {
    val name: String
    val value: String
}

class SettingsDownloadConversionHeaderType(
    override val name: String,
    override val value: String,
) : SettingsDownloadConversionHeader
