package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Scalars need defaults (proto3 peers omit zero values); see BackupManga.
@Serializable
data class BackupSource(
    @ProtoNumber(1) var name: String = "",
    @ProtoNumber(2) @EncodeDefault var sourceId: Long = 0,
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
)
