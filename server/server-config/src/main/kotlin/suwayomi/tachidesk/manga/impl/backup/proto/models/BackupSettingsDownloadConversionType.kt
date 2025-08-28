package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversion

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class BackupSettingsDownloadConversionType(
    @ProtoNumber(1) override val mimeType: String,
    @ProtoNumber(2) override val target: String,
    @ProtoNumber(3) override val compressionLevel: Double?,
) : SettingsDownloadConversion