package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversion
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionHeader
import kotlin.time.Duration

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class BackupSettingsDownloadConversionType(
    @ProtoNumber(1) override val mimeType: String,
    @ProtoNumber(2) override val target: String,
    @ProtoNumber(3) override val compressionLevel: Double? = null,
    @ProtoNumber(4) override val callTimeout: Duration? = null,
    @ProtoNumber(5) override val connectTimeout: Duration? = null,
    @ProtoNumber(6) override val headers: List<BackupSettingsDownloadConversionHeaderType>? = null
) : SettingsDownloadConversion

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class BackupSettingsDownloadConversionHeaderType(
    @ProtoNumber(1) override val name: String,
    @ProtoNumber(2) override val value: String

): SettingsDownloadConversionHeader
